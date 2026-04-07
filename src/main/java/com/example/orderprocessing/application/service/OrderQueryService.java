package com.example.orderprocessing.application.service;

import com.example.orderprocessing.interfaces.http.dto.OrderResponse;
import com.example.orderprocessing.application.exception.NotFoundException;
import com.example.orderprocessing.application.port.CacheProvider;
import com.example.orderprocessing.application.port.OrderRecord;
import com.example.orderprocessing.application.port.OrderRepository;
import com.example.orderprocessing.domain.order.OrderStatus;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
/**
 * Application-layer query service (CQRS read side) for order retrieval.
 *
 * <p>Combines repository reads with cache-aside strategy and request coalescing to reduce
 * repeated DB load on hot keys while preserving response semantics.</p>
 */
public class OrderQueryService {

    private final OrderRepository orderRepository;
    private final CacheProvider cacheProvider;
    private final OrderMapper mapper;
    private final Timer queryTimer;
    private final Timer dbQueryTimer;
    private final Counter requestCounter;
    private final Counter errorCounter;
    private final Duration orderByIdTtl;
    private final Duration orderListTtl;
    private final int listMaxRows;
    private final ObservationRegistry observationRegistry;
    private final ConcurrentHashMap<String, Object> keyLocks = new ConcurrentHashMap<>();

    /**
     * Creates the read-side order query service.
     * @param orderRepository order repository port
     * @param orderMapper mapper used for domain/API conversion
     * @param cacheProvider cache provider for read acceleration
     */
    public OrderQueryService(OrderRepository orderRepository,
                             CacheProvider cacheProvider,
                             OrderMapper mapper,
                             MeterRegistry meterRegistry,
                             ObservationRegistry observationRegistry,
                             @Value("${app.cache.ttl.order-by-id-seconds:300}") long orderByIdTtlSeconds,
                             @Value("${app.cache.ttl.order-list-seconds:60}") long orderListTtlSeconds,
                             @Value("${app.query.list-max-rows:1000}") int listMaxRows) {
        this.orderRepository = orderRepository;
        this.cacheProvider = cacheProvider;
        this.mapper = mapper;
        this.observationRegistry = observationRegistry;
        this.queryTimer = meterRegistry.timer("orders.query.duration");
        this.dbQueryTimer = Timer.builder("db.query.duration")
                .description("Repository query latency (exemplar-enabled with active trace context)")
                .publishPercentileHistogram(true)
                .register(meterRegistry);
        this.requestCounter = meterRegistry.counter("orders.query.request.count");
        this.errorCounter = meterRegistry.counter("orders.query.error.count");
        this.orderByIdTtl = Duration.ofSeconds(orderByIdTtlSeconds);
        this.orderListTtl = Duration.ofSeconds(orderListTtlSeconds);
        this.listMaxRows = Math.max(1, listMaxRows);
    }

    @Transactional(transactionManager = "transactionManager", readOnly = true)
    /**
     * Retrieves a single order projection, preferring cache when available.
     *
     * <p>On cache miss, loads from repository and caches result with by-id TTL.
     * Concurrent misses for the same key are coalesced to one backing read.</p>
     *
     * <p>Non-admin callers only resolve orders they own ({@code ownerSubject} match); others receive
     * {@link NotFoundException} to avoid leaking existence.</p>
     *
     * @param id order identifier
     * @param viewerSubject authenticated principal name (JWT {@code sub})
     * @param viewerIsAdmin when {@code true}, any order id may be loaded
     * @return order response
     */
    public OrderResponse getById(UUID id, String viewerSubject, boolean viewerIsAdmin) {
        requestCounter.increment();
        return queryTimer.record(() -> {
            try {
                String key = viewerIsAdmin
                        ? OrderReadCacheKeys.orderByIdAdmin(id)
                        : OrderReadCacheKeys.orderByIdUser(viewerSubject, id);
                return cacheProvider.get(key, OrderResponse.class)
                        .orElseGet(() -> coalescedOrderLoad(key, () -> {
                            OrderRecord record = viewerIsAdmin
                                    ? findEntity(id)
                                    : orderRepository.findByIdAndOwnerSubject(id, viewerSubject).orElseThrow(
                                            () -> new NotFoundException("Order not found: " + id));
                            OrderResponse loaded = mapper.toResponse(mapper.toDomain(record));
                            cacheProvider.put(key, loaded, orderByIdTtl);
                            return loaded;
                        }));
            } catch (RuntimeException ex) {
                errorCounter.increment();
                throw ex;
            }
        });
    }

    @Transactional(transactionManager = "transactionManager", readOnly = true)
    /**
     * Retrieves list of orders optionally filtered by status.
     *
     * <p>Uses status-scoped cache entries and coalesces concurrent misses to avoid thundering herd
     * behavior on expensive list queries.</p>
     *
     * <p>Non-admin callers only see orders they created.</p>
     *
     * @param status optional status filter, or {@code null} for all orders for this viewer
     * @param viewerSubject authenticated principal name (JWT {@code sub})
     * @param viewerIsAdmin when {@code true}, returns orders across all owners (bounded by list max rows)
     * @return order responses for the selected scope
     */
    public List<OrderResponse> list(OrderStatus status, String viewerSubject, boolean viewerIsAdmin) {
        requestCounter.increment();
        return queryTimer.record(() -> {
            try {
                String key = viewerIsAdmin
                        ? OrderReadCacheKeys.listByStatusAdmin(status)
                        : OrderReadCacheKeys.listByStatusUser(viewerSubject, status);
                return cacheProvider.get(key, CachedOrderList.class)
                        .map(CachedOrderList::orders)
                        .orElseGet(() -> coalescedStatusLoad(key, () -> {
                            var pageable = PageRequest.of(0, listMaxRows, Sort.by("createdAt").ascending());
                            var page = recordDbQuery("orders.list", () -> {
                                if (viewerIsAdmin) {
                                    return status == null
                                            ? orderRepository.findAll(pageable)
                                            : orderRepository.findByStatus(status, pageable);
                                }
                                return status == null
                                        ? orderRepository.findByOwnerSubject(viewerSubject, pageable)
                                        : orderRepository.findByOwnerSubjectAndStatus(viewerSubject, status, pageable);
                            });
                            List<OrderRecord> orders = page.getContent();
                            List<OrderResponse> loaded = orders.stream().map(mapper::toDomain).map(mapper::toResponse).toList();
                            cacheProvider.put(key, new CachedOrderList(loaded), orderListTtl);
                            return loaded;
                        }));
            } catch (RuntimeException ex) {
                errorCounter.increment();
                throw ex;
            }
        });
    }

    @Transactional(transactionManager = "transactionManager", readOnly = true)
    /**
     * Paginated list; non-admin callers only see their own orders.
     */
    public PagedOrderResult listPage(OrderStatus status, int page, int size, String viewerSubject, boolean viewerIsAdmin) {
        requestCounter.increment();
        return queryTimer.record(() -> {
            try {
                int normalizedPage = Math.max(0, page);
                int normalizedSize = Math.min(500, Math.max(1, size));
                var pageable = PageRequest.of(normalizedPage, normalizedSize, Sort.by("createdAt").ascending());
                var results = recordDbQuery("orders.list.page", () -> {
                    if (viewerIsAdmin) {
                        return status == null
                                ? orderRepository.findAll(pageable)
                                : orderRepository.findByStatus(status, pageable);
                    }
                    return status == null
                            ? orderRepository.findByOwnerSubject(viewerSubject, pageable)
                            : orderRepository.findByOwnerSubjectAndStatus(viewerSubject, status, pageable);
                });
                List<OrderResponse> orders = results.getContent().stream()
                        .map(mapper::toDomain)
                        .map(mapper::toResponse)
                        .toList();
                return new PagedOrderResult(
                        orders,
                        results.getNumber(),
                        results.getSize(),
                        results.getTotalElements(),
                        results.getTotalPages());
            } catch (RuntimeException ex) {
                errorCounter.increment();
                throw ex;
            }
        });
    }

    private OrderRecord findEntity(UUID id) {
        return recordDbQuery("orders.getById",
                () -> orderRepository.findById(id).orElseThrow(() -> new NotFoundException("Order not found: " + id)));
    }

    private <T> T recordDbQuery(String operation, java.util.function.Supplier<T> supplier) {
        Observation observation = Observation.start("db.query.duration", observationRegistry)
                .lowCardinalityKeyValue("operation", operation);
        Timer.Sample sample = Timer.start();
        try (Observation.Scope scope = observation.openScope()) {
            T result = supplier.get();
            sample.stop(dbQueryTimer);
            observation.stop();
            return result;
        } catch (RuntimeException ex) {
            sample.stop(dbQueryTimer);
            observation.error(ex);
            observation.stop();
            throw ex;
        }
    }

    private OrderResponse coalescedOrderLoad(String key, java.util.function.Supplier<OrderResponse> loader) {
        Object lock = keyLocks.computeIfAbsent(key, k -> new Object());
        try {
            synchronized (lock) {
                OrderResponse existing = cacheProvider.get(key, OrderResponse.class).orElse(null);
                if (existing != null) {
                    return existing;
                }
                return loader.get();
            }
        } finally {
            keyLocks.remove(key, lock);
        }
    }

    private List<OrderResponse> coalescedStatusLoad(String key, java.util.function.Supplier<List<OrderResponse>> loader) {
        Object lock = keyLocks.computeIfAbsent(key, k -> new Object());
        try {
            synchronized (lock) {
                CachedOrderList existing = cacheProvider.get(key, CachedOrderList.class).orElse(null);
                if (existing != null) {
                    return existing.orders();
                }
                return loader.get();
            }
        } finally {
            keyLocks.remove(key, lock);
        }
    }

    public record PagedOrderResult(
            List<OrderResponse> orders,
            int page,
            int size,
            long totalElements,
            int totalPages) {
    }
}
