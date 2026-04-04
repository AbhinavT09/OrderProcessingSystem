package com.example.orderprocessing.application.service;

import com.example.orderprocessing.interfaces.http.dto.CreateOrderRequest;
import com.example.orderprocessing.interfaces.http.dto.OrderResponse;
import com.example.orderprocessing.application.event.OrderCreatedEvent;
import com.example.orderprocessing.application.exception.ConflictException;
import com.example.orderprocessing.application.exception.InfrastructureException;
import com.example.orderprocessing.application.exception.NotFoundException;
import com.example.orderprocessing.application.port.CacheProvider;
import com.example.orderprocessing.application.port.OrderRepository;
import com.example.orderprocessing.domain.order.Order;
import com.example.orderprocessing.domain.order.OrderStatus;
import com.example.orderprocessing.infrastructure.crosscutting.GlobalIdempotencyService;
import com.example.orderprocessing.infrastructure.persistence.entity.OrderEntity;
import com.example.orderprocessing.infrastructure.resilience.RegionalFailoverManager;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.annotation.Transactional;

@Service
/**
 * Application-layer command service (CQRS write side) for order lifecycle changes.
 *
 * <p>Responsibilities:</p>
 * <ul>
 *   <li>Coordinates idempotent order creation using global lifecycle states.</li>
 *   <li>Persists aggregate state via repository ports and emits outbox events.</li>
 *   <li>Invalidates read caches after successful writes.</li>
 *   <li>Enforces regional write gating through failover policy.</li>
 * </ul>
 */
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final CacheProvider cacheProvider;
    private final OutboxService outboxService;
    private final OrderMapper mapper;
    private final GlobalIdempotencyService globalIdempotencyService;
    private final RegionalFailoverManager failoverManager;
    private final Counter createCounter;
    private final Counter requestCounter;
    private final Counter idempotentHitCounter;
    private final Counter failureCounter;
    private final Timer operationTimer;

    /**
     * Creates the write-side order application service.
     * @param orderRepository order repository port
     * @param orderMapper mapper used for domain/API conversion
     * @param outboxService outbox write orchestrator
     * @param cacheProvider cache provider for invalidation
     * @param meterRegistry metrics registry
     * @param regionalFailoverManager region write-gating controller
     * @param globalIdempotencyService cross-region idempotency coordinator
     */
    public OrderService(OrderRepository orderRepository,
                        CacheProvider cacheProvider,
                        OutboxService outboxService,
                        OrderMapper mapper,
                        GlobalIdempotencyService globalIdempotencyService,
                        RegionalFailoverManager failoverManager,
                        MeterRegistry meterRegistry) {
        this.orderRepository = orderRepository;
        this.cacheProvider = cacheProvider;
        this.outboxService = outboxService;
        this.mapper = mapper;
        this.globalIdempotencyService = globalIdempotencyService;
        this.failoverManager = failoverManager;
        this.requestCounter = meterRegistry.counter("orders.service.request.count");
        this.createCounter = meterRegistry.counter("orders.created.count");
        this.idempotentHitCounter = meterRegistry.counter("orders.idempotency.hit.count");
        this.failureCounter = meterRegistry.counter("orders.operation.failure.count");
        this.operationTimer = meterRegistry.timer("orders.operation.duration");
    }

    @Transactional
    /**
     * Creates an order while enforcing request-level idempotency semantics.
     *
     * <p>Idempotency lifecycle handling:</p>
     * <ul>
     *   <li><b>COMPLETED</b>: returns the previously created order response.</li>
     *   <li><b>IN_PROGRESS</b>: rejects concurrent duplicate processing with conflict.</li>
     *   <li><b>ABSENT</b>: marks key IN_PROGRESS, then executes write flow.</li>
     * </ul>
     *
     * <p>Side effects include:</p>
     * <ul>
     *   <li>DB write of {@code OrderEntity}.</li>
     *   <li>Transactional outbox enqueue for {@code ORDER_CREATED}.</li>
     *   <li>Cache invalidation of order-list views.</li>
     *   <li>After-commit transition of idempotency key to COMPLETED with order id.</li>
     * </ul>
     *
     * @param request validated create-order payload from interface layer
     * @param idempotencyKey optional idempotency key from request header
     * @return created or previously completed order response
     */
    public OrderResponse createOrder(CreateOrderRequest request, String idempotencyKey) {
        assertWriteAllowed();
        requestCounter.increment();
        return operationTimer.record(() -> {
            String normalizedIdempotencyKey = normalizeIdempotencyKey(idempotencyKey);

            if (normalizedIdempotencyKey != null) {
                GlobalIdempotencyService.IdempotencyState state =
                        globalIdempotencyService.resolveState(normalizedIdempotencyKey);

                if (state.hasCompletedOrder()) {
                    OrderEntity existingGlobal = orderRepository.findById(state.orderId()).orElse(null);
                    if (existingGlobal != null) {
                        idempotentHitCounter.increment();
                        log.info("Global idempotency COMPLETED reuse key={} orderId={}",
                                normalizedIdempotencyKey, existingGlobal.getId());
                        return mapper.toResponse(mapper.toDomain(existingGlobal));
                    }
                }

                if (state.status() == GlobalIdempotencyService.IdempotencyStatus.IN_PROGRESS) {
                    log.info("Global idempotency IN_PROGRESS detected key={} timestamp={}",
                            normalizedIdempotencyKey, state.timestamp());
                    throw new ConflictException("Duplicate request in progress for idempotency key");
                }

                if (!globalIdempotencyService.markInProgress(normalizedIdempotencyKey)) {
                    GlobalIdempotencyService.IdempotencyState latestState =
                            globalIdempotencyService.resolveState(normalizedIdempotencyKey);
                    if (latestState.status() == GlobalIdempotencyService.IdempotencyStatus.IN_PROGRESS) {
                        log.info("Global idempotency IN_PROGRESS detected key={} timestamp={}",
                                normalizedIdempotencyKey, latestState.timestamp());
                    }
                    throw new ConflictException("Duplicate request in progress for idempotency key");
                }
            }

            if (normalizedIdempotencyKey != null) {
                OrderEntity existing = orderRepository.findByIdempotencyKey(normalizedIdempotencyKey).orElse(null);
                if (existing != null) {
                    idempotentHitCounter.increment();
                    log.info("Idempotent order create hit key={} orderId={}", normalizedIdempotencyKey, existing.getId());
                    globalIdempotencyService.markCompleted(normalizedIdempotencyKey, existing.getId());
                    return mapper.toResponse(mapper.toDomain(existing));
                }
            }

            Order order = Order.create(mapper.toDomainItems(request.items()), normalizedIdempotencyKey);

            try {
                OrderEntity saved = orderRepository.save(mapper.toEntity(order));
                MDC.put("order_id", saved.getId().toString());

                OrderCreatedEvent createdEvent = new OrderCreatedEvent(
                        2,
                        UUID.randomUUID().toString(),
                        "ORDER_CREATED",
                        saved.getId().toString(),
                        Instant.now().toString());
                outboxService.enqueueOrderCreated(saved.getId().toString(), createdEvent);
                invalidateStatusCaches();
                markGlobalIdempotencyCompletedAfterCommit(normalizedIdempotencyKey, saved.getId());

                createCounter.increment();
                log.info("Order created orderId={} status={} idempotencyKey={}",
                        saved.getId(), saved.getStatus(), normalizedIdempotencyKey);
                return mapper.toResponse(mapper.toDomain(saved));
            } catch (DataIntegrityViolationException ex) {
                if (normalizedIdempotencyKey == null) {
                    failureCounter.increment();
                    throw new ConflictException("Order create conflict");
                }
                OrderEntity existing = orderRepository.findByIdempotencyKey(normalizedIdempotencyKey)
                        .orElseThrow(() -> new ConflictException("Idempotency conflict while creating order"));
                idempotentHitCounter.increment();
                globalIdempotencyService.markCompleted(normalizedIdempotencyKey, existing.getId());
                log.warn("Order create deduped after race key={} orderId={}", normalizedIdempotencyKey, existing.getId());
                return mapper.toResponse(mapper.toDomain(existing));
            } finally {
                MDC.remove("order_id");
            }
        });
    }

    @Transactional
    /**
     * Updates order status with optimistic-concurrency protection.
     *
     * <p>Uses caller-provided expected version and retries once on optimistic lock races.
     * A conflict is returned when concurrent updates cannot be reconciled safely.</p>
     *
     * @param id order identifier
     * @param status target status transition requested by caller
     * @param expectedVersion version expected by caller for compare-and-set semantics
     * @return updated order response
     */
    public OrderResponse updateStatus(UUID id, OrderStatus status, Long expectedVersion) {
        assertWriteAllowed();
        requestCounter.increment();
        return operationTimer.record(() -> {
            MDC.put("order_id", id.toString());
            try {
                int attempts = 0;
                while (attempts < 2) {
                    attempts++;
                    OrderEntity entity = findEntity(id);
                    checkExpectedVersion(expectedVersion, entity.getVersion(), id);
                    Order order = mapper.toDomain(entity);
                    OrderStatus previous = order.getStatus();
                    order.updateStatus(status);
                    try {
                        OrderEntity saved = orderRepository.save(mapper.toEntity(order));
                        invalidateOrderCaches(saved.getId());
                        log.info("Order status updated orderId={} from={} to={} version={}",
                                id, previous, status, saved.getVersion());
                        return mapper.toResponse(mapper.toDomain(saved));
                    } catch (ObjectOptimisticLockingFailureException ex) {
                        if (attempts >= 2) {
                            failureCounter.increment();
                            throw new ConflictException("Concurrent update detected for order: " + id);
                        }
                    }
                }
                throw new ConflictException("Concurrent update detected for order: " + id);
            } finally {
                MDC.remove("order_id");
            }
        });
    }

    @Transactional
    /**
     * Cancels an order when domain invariants allow cancellation.
     *
     * <p>Applies the same optimistic-concurrency retry policy as status updates and
     * invalidates cached read models after persistence succeeds.</p>
     *
     * @param id order identifier
     * @return cancelled order response
     */
    public OrderResponse cancel(UUID id) {
        assertWriteAllowed();
        requestCounter.increment();
        return operationTimer.record(() -> {
            MDC.put("order_id", id.toString());
            try {
                int attempts = 0;
                while (attempts < 2) {
                    attempts++;
                    Order order = mapper.toDomain(findEntity(id));
                    order.cancel();
                    try {
                        OrderEntity saved = orderRepository.save(mapper.toEntity(order));
                        invalidateOrderCaches(saved.getId());
                        log.info("Order cancelled orderId={} version={}", id, saved.getVersion());
                        return mapper.toResponse(mapper.toDomain(saved));
                    } catch (ObjectOptimisticLockingFailureException ex) {
                        if (attempts >= 2) {
                            failureCounter.increment();
                            throw new ConflictException("Concurrent cancel/update detected for order: " + id);
                        }
                    }
                }
                throw new ConflictException("Concurrent cancel/update detected for order: " + id);
            } finally {
                MDC.remove("order_id");
            }
        });
    }

    private OrderEntity findEntity(UUID id) {
        return orderRepository.findById(id).orElseThrow(() -> new NotFoundException("Order not found: " + id));
    }

    private void invalidateOrderCaches(UUID orderId) {
        cacheProvider.evict("order:id:" + orderId);
        invalidateStatusCaches();
    }

    private void invalidateStatusCaches() {
        cacheProvider.evict("orders:status:ALL");
        for (OrderStatus status : OrderStatus.values()) {
            cacheProvider.evict("orders:status:" + status.name());
        }
    }

    private void checkExpectedVersion(Long expectedVersion, Long actualVersion, UUID orderId) {
        if (expectedVersion == null || actualVersion == null) {
            throw new ConflictException("Order version mismatch for order: " + orderId);
        }
        if (!expectedVersion.equals(actualVersion)) {
            throw new ConflictException("Order version mismatch for order: " + orderId);
        }
    }

    private String normalizeIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null) {
            return null;
        }
        String trimmed = idempotencyKey.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void assertWriteAllowed() {
        if (!failoverManager.allowsWrites()) {
            throw new InfrastructureException("Region in passive mode; writes are temporarily disabled", null);
        }
    }

    private void markGlobalIdempotencyCompletedAfterCommit(String idempotencyKey, UUID orderId) {
        if (idempotencyKey == null || orderId == null) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    globalIdempotencyService.markCompleted(idempotencyKey, orderId);
                }
            });
            return;
        }
        // Defensive fallback in case method is reused outside a transaction boundary.
        globalIdempotencyService.markCompleted(idempotencyKey, orderId);
    }
}
