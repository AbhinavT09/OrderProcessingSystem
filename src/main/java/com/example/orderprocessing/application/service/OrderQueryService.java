package com.example.orderprocessing.application.service;

import com.example.orderprocessing.interfaces.http.dto.OrderResponse;
import com.example.orderprocessing.application.exception.NotFoundException;
import com.example.orderprocessing.application.port.CacheProvider;
import com.example.orderprocessing.application.port.OrderRepository;
import com.example.orderprocessing.domain.order.OrderStatus;
import com.example.orderprocessing.infrastructure.persistence.entity.OrderEntity;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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
    private final Counter requestCounter;
    private final Counter errorCounter;
    private final Duration orderByIdTtl;
    private final Duration orderListTtl;
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
                             @Value("${app.cache.ttl.order-by-id-seconds:300}") long orderByIdTtlSeconds,
                             @Value("${app.cache.ttl.order-list-seconds:60}") long orderListTtlSeconds) {
        this.orderRepository = orderRepository;
        this.cacheProvider = cacheProvider;
        this.mapper = mapper;
        this.queryTimer = meterRegistry.timer("orders.query.duration");
        this.requestCounter = meterRegistry.counter("orders.query.request.count");
        this.errorCounter = meterRegistry.counter("orders.query.error.count");
        this.orderByIdTtl = Duration.ofSeconds(orderByIdTtlSeconds);
        this.orderListTtl = Duration.ofSeconds(orderListTtlSeconds);
    }

    @Transactional(transactionManager = "transactionManager", readOnly = true)
    /**
     * Retrieves a single order projection, preferring cache when available.
     *
     * <p>On cache miss, loads from repository and caches result with by-id TTL.
     * Concurrent misses for the same key are coalesced to one backing read.</p>
     *
     * @param id order identifier
     * @return order response
     */
    public OrderResponse getById(UUID id) {
        requestCounter.increment();
        return queryTimer.record(() -> {
            try {
                String key = orderCacheKey(id);
                return cacheProvider.get(key, OrderResponse.class)
                        .orElseGet(() -> coalescedOrderLoad(key, () -> {
                            OrderResponse loaded = mapper.toResponse(mapper.toDomain(findEntity(id)));
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
     * @param status optional status filter, or {@code null} for all orders
     * @return order responses for the selected scope
     */
    public List<OrderResponse> list(OrderStatus status) {
        requestCounter.increment();
        return queryTimer.record(() -> {
            try {
                String key = statusCacheKey(status);
                return cacheProvider.get(key, CachedOrderList.class)
                        .map(CachedOrderList::orders)
                        .orElseGet(() -> coalescedStatusLoad(key, () -> {
                            List<OrderEntity> orders = status == null ? orderRepository.findAll() : orderRepository.findByStatus(status);
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

    private OrderEntity findEntity(UUID id) {
        return orderRepository.findById(id).orElseThrow(() -> new NotFoundException("Order not found: " + id));
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

    private String orderCacheKey(UUID id) {
        return "order:id:" + id;
    }

    private String statusCacheKey(OrderStatus status) {
        return "orders:status:" + (status == null ? "ALL" : status.name());
    }
}
