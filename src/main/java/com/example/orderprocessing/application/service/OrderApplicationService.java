package com.example.orderprocessing.application.service;

import com.example.orderprocessing.api.dto.CreateOrderRequest;
import com.example.orderprocessing.api.dto.OrderResponse;
import com.example.orderprocessing.application.exception.ConflictException;
import com.example.orderprocessing.application.exception.NotFoundException;
import com.example.orderprocessing.application.port.OrderRepositoryPort;
import com.example.orderprocessing.domain.model.OrderStatus;
import com.example.orderprocessing.infrastructure.persistence.entity.OrderEntity;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderApplicationService {

    private static final Logger log = LoggerFactory.getLogger(OrderApplicationService.class);

    private final OrderRepositoryPort orderRepository;
    private final OutboxService outboxService;
    private final OrderMapper mapper;
    private final Counter createCounter;
    private final Counter idempotentHitCounter;
    private final Counter failureCounter;
    private final Timer operationTimer;

    public OrderApplicationService(OrderRepositoryPort orderRepository,
                                   OutboxService outboxService,
                                   OrderMapper mapper,
                                   MeterRegistry meterRegistry) {
        this.orderRepository = orderRepository;
        this.outboxService = outboxService;
        this.mapper = mapper;
        this.createCounter = meterRegistry.counter("orders.created.count");
        this.idempotentHitCounter = meterRegistry.counter("orders.idempotency.hit.count");
        this.failureCounter = meterRegistry.counter("orders.operation.failure.count");
        this.operationTimer = meterRegistry.timer("orders.operation.duration");
    }

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request, String idempotencyKey) {
        return operationTimer.record(() -> {
            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                OrderEntity existing = orderRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
                if (existing != null) {
                    idempotentHitCounter.increment();
                    log.info("Idempotent order create hit key={} orderId={}", idempotencyKey, existing.getId());
                    return mapper.toResponse(existing);
                }
            }

            OrderEntity order = new OrderEntity();
            order.setId(UUID.randomUUID());
            order.setStatus(OrderStatus.PENDING);
            order.setCreatedAt(Instant.now());
            order.setItems(mapper.toEmbeddables(request.items()));
            order.setIdempotencyKey(idempotencyKey);

            try {
                OrderEntity saved = orderRepository.save(order);
                outboxService.enqueue("Order", saved.getId().toString(), "ORDER_CREATED", "{\"status\":\"PENDING\"}");
                createCounter.increment();
                log.info("Order created orderId={} status={} idempotencyKey={}",
                        saved.getId(), saved.getStatus(), idempotencyKey);
                return mapper.toResponse(saved);
            } catch (DataIntegrityViolationException ex) {
                if (idempotencyKey == null || idempotencyKey.isBlank()) {
                    failureCounter.increment();
                    throw new ConflictException("Order create conflict");
                }
                // Unique key race: concurrent duplicate idempotency key request.
                OrderEntity existing = orderRepository.findByIdempotencyKey(idempotencyKey)
                        .orElseThrow(() -> new ConflictException("Idempotency conflict while creating order"));
                idempotentHitCounter.increment();
                log.warn("Order create deduped after race key={} orderId={}", idempotencyKey, existing.getId());
                return mapper.toResponse(existing);
            }
        });
    }

    @Transactional(readOnly = true)
    public OrderResponse getById(UUID id) {
        return operationTimer.record(() -> mapper.toResponse(find(id)));
    }

    @Transactional
    public OrderResponse updateStatus(UUID id, OrderStatus status) {
        return operationTimer.record(() -> {
            OrderEntity order = find(id);
            OrderStatus previous = order.getStatus();
            validateTransition(order.getStatus(), status);
            order.setStatus(status);
            try {
                OrderEntity saved = orderRepository.save(order);
                outboxService.enqueue("Order", saved.getId().toString(), "ORDER_STATUS_UPDATED", "{\"status\":\"" + status + "\"}");
                log.info("Order status updated orderId={} from={} to={}", id, previous, status);
                return mapper.toResponse(saved);
            } catch (ObjectOptimisticLockingFailureException ex) {
                failureCounter.increment();
                throw new ConflictException("Concurrent update detected for order: " + id);
            }
        });
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> list(OrderStatus status) {
        return operationTimer.record(() -> {
            List<OrderEntity> orders = status == null ? orderRepository.findAll() : orderRepository.findByStatus(status);
            return orders.stream().map(mapper::toResponse).toList();
        });
    }

    @Transactional
    public OrderResponse cancel(UUID id) {
        return operationTimer.record(() -> {
            OrderEntity order = find(id);
            if (order.getStatus() != OrderStatus.PENDING) {
                throw new ConflictException("Only PENDING orders can be cancelled");
            }

            order.setStatus(OrderStatus.CANCELLED);
            try {
                OrderEntity saved = orderRepository.save(order);
                outboxService.enqueue("Order", saved.getId().toString(), "ORDER_CANCELLED", "{\"status\":\"CANCELLED\"}");
                log.info("Order cancelled orderId={}", id);
                return mapper.toResponse(saved);
            } catch (ObjectOptimisticLockingFailureException ex) {
                failureCounter.increment();
                throw new ConflictException("Concurrent cancel/update detected for order: " + id);
            }
        });
    }

    @Transactional
    public int promotePendingToProcessing() {
        return operationTimer.record(() -> {
            int promotedCount = 0;
            List<OrderEntity> pending = orderRepository.findByStatus(OrderStatus.PENDING);
            for (OrderEntity order : pending) {
                try {
                    order.setStatus(OrderStatus.PROCESSING);
                    orderRepository.save(order);
                    outboxService.enqueue("Order", order.getId().toString(), "ORDER_STATUS_UPDATED", "{\"status\":\"PROCESSING\"}");
                    promotedCount++;
                } catch (ObjectOptimisticLockingFailureException ex) {
                    log.warn("Skipped stale pending promotion due to concurrent update orderId={}", order.getId());
                }
            }
            if (promotedCount > 0) {
                log.info("Promoted pending orders count={}", promotedCount);
            }
            return promotedCount;
        });
    }

    private OrderEntity find(UUID id) {
        return orderRepository.findById(id).orElseThrow(() -> new NotFoundException("Order not found: " + id));
    }

    private void validateTransition(OrderStatus current, OrderStatus target) {
        if (current == OrderStatus.CANCELLED || current == OrderStatus.DELIVERED) {
            throw new ConflictException("Terminal order status cannot be changed");
        }
        if (current == target) {
            throw new ConflictException("Order is already in status: " + target);
        }
    }
}
