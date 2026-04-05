package com.example.orderprocessing.application.service;

import com.example.orderprocessing.application.port.CacheProvider;
import com.example.orderprocessing.application.port.OrderRecord;
import com.example.orderprocessing.application.port.OrderRepository;
import com.example.orderprocessing.domain.order.OrderStatus;
import com.example.orderprocessing.infrastructure.crosscutting.GlobalIdempotencyService;
import com.example.orderprocessing.infrastructure.resilience.BackpressureManager;
import com.example.orderprocessing.infrastructure.resilience.RegionalConsistencyManager;
import com.example.orderprocessing.interfaces.http.dto.CreateOrderRequest;
import com.example.orderprocessing.interfaces.http.dto.OrderItemRequest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.dao.DataIntegrityViolationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrderServiceIdempotencyLifecycleTest {

    @Test
    void sameRequestSameKeyReturnsSameOrder() {
        InMemoryOrderRepository repository = new InMemoryOrderRepository();
        TestGlobalIdempotencyService idempotency = new TestGlobalIdempotencyService();
        OrderService service = buildService(repository, idempotency);

        CreateOrderRequest request = request("Keyboard");
        String key = "idem-same-key";

        UUID first = service.createOrder(request, key).id();
        UUID second = service.createOrder(request, key).id();

        assertEquals(first, second);
        assertEquals(1, repository.size());
    }

    @Test
    void inProgressStatePreventsDuplicateThenRetrySucceeds() {
        InMemoryOrderRepository repository = new InMemoryOrderRepository();
        TestGlobalIdempotencyService idempotency = new TestGlobalIdempotencyService();
        OrderService service = buildService(repository, idempotency);

        String key = "idem-crash-recovery";
        idempotency.forceInProgress(key);

        try {
            service.createOrder(request("Mouse"), key);
            fail("Expected conflict while key is IN_PROGRESS");
        } catch (Exception ex) {
            assertTrue(ex.getMessage().contains("Duplicate request in progress"));
        }

        // Simulate expiry/recovery after crash.
        idempotency.clear(key);

        UUID created = service.createOrder(request("Mouse"), key).id();
        assertNotNull(created);
        assertEquals(1, repository.size());
    }

    @Test
    void completedStateAlwaysReusesSameOrder() {
        InMemoryOrderRepository repository = new InMemoryOrderRepository();
        TestGlobalIdempotencyService idempotency = new TestGlobalIdempotencyService();
        OrderService service = buildService(repository, idempotency);

        String key = "idem-completed-reuse";
        UUID first = service.createOrder(request("Adapter"), key).id();
        UUID second = service.createOrder(request("Adapter"), key).id();
        UUID third = service.createOrder(request("Adapter"), key).id();

        assertEquals(first, second);
        assertEquals(first, third);
        assertEquals(1, repository.size());
    }

    @Test
    void concurrentRetriesDoNotCreateDuplicateOrders() throws Exception {
        InMemoryOrderRepository repository = new InMemoryOrderRepository();
        TestGlobalIdempotencyService idempotency = new TestGlobalIdempotencyService();
        OrderService service = buildService(repository, idempotency);

        String key = "idem-race";
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        List<UUID> successes = new CopyOnWriteArrayList<>();
        List<String> failures = new CopyOnWriteArrayList<>();

        Runnable call = () -> {
            ready.countDown();
            try {
                go.await(2, TimeUnit.SECONDS);
                UUID id = service.createOrder(request("Cable"), key).id();
                successes.add(id);
            } catch (Exception ex) {
                failures.add(ex.getMessage());
            }
        };

        Thread t1 = new Thread(call);
        Thread t2 = new Thread(call);
        t1.start();
        t2.start();
        ready.await(2, TimeUnit.SECONDS);
        go.countDown();
        t1.join();
        t2.join();

        assertEquals(1, repository.size());
        assertEquals(1, successes.size());
        assertEquals(1, failures.size());
        assertTrue(failures.get(0).contains("Duplicate request in progress")
                || failures.get(0).contains("Idempotency conflict while creating order"));
    }

    private OrderService buildService(InMemoryOrderRepository repository, TestGlobalIdempotencyService idempotency) {
        CacheProvider cacheProvider = new NoOpCacheProvider();
        OutboxService outbox = mock(OutboxService.class);
        OrderMapper mapper = new OrderMapper();
        RegionalConsistencyManager regionalConsistencyManager = mock(RegionalConsistencyManager.class);
        BackpressureManager backpressureManager = mock(BackpressureManager.class);
        when(regionalConsistencyManager.allowsWrites()).thenReturn(true);
        when(regionalConsistencyManager.regionId()).thenReturn("region-a");
        when(backpressureManager.shouldRejectWrites()).thenReturn(false);
        return new OrderService(
                repository,
                cacheProvider,
                outbox,
                mapper,
                idempotency,
                regionalConsistencyManager,
                backpressureManager,
                new SimpleMeterRegistry());
    }

    private CreateOrderRequest request(String productName) {
        return new CreateOrderRequest(List.of(new OrderItemRequest(productName, 1, 10.0)));
    }

    private static final class NoOpCacheProvider implements CacheProvider {
        @Override
        public <T> Optional<T> get(String key, Class<T> type) {
            return Optional.empty();
        }

        @Override
        public void put(String key, Object value) {
        }

        @Override
        public void put(String key, Object value, Duration ttl) {
        }

        @Override
        public void evict(String key) {
        }
    }

    private static final class InMemoryOrderRepository implements OrderRepository {
        private final Map<UUID, OrderRecord> byId = new ConcurrentHashMap<>();
        private final Map<String, UUID> byIdempotencyKey = new ConcurrentHashMap<>();

        @Override
        public synchronized OrderRecord save(OrderRecord order) {
            OrderRecord normalized = order;
            if (order.id() == null || order.createdAt() == null || order.status() == null || order.version() == null) {
                normalized = new OrderRecord(
                        order.id() == null ? UUID.randomUUID() : order.id(),
                        order.version() == null ? 0L : order.version(),
                        order.status() == null ? OrderStatus.PENDING : order.status(),
                        order.createdAt() == null ? Instant.now() : order.createdAt(),
                        order.idempotencyKey(),
                        order.regionId(),
                        order.lastUpdatedTimestamp(),
                        order.items());
            }
            String key = normalized.idempotencyKey();
            if (key != null) {
                UUID existing = byIdempotencyKey.get(key);
                if (existing != null && !existing.equals(normalized.id())) {
                    throw new DataIntegrityViolationException("duplicate idempotency key");
                }
                byIdempotencyKey.put(key, normalized.id());
            }
            byId.put(normalized.id(), normalized);
            return normalized;
        }

        @Override
        public Optional<OrderRecord> findById(UUID id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public List<OrderRecord> findAll() {
            return new ArrayList<>(byId.values());
        }

        @Override
        public List<OrderRecord> findByStatus(OrderStatus status) {
            return byId.values().stream().filter(e -> status == e.status()).toList();
        }

        @Override
        public Page<OrderRecord> findAll(Pageable pageable) {
            List<OrderRecord> all = findAll();
            int start = (int) Math.min((long) pageable.getPageNumber() * pageable.getPageSize(), all.size());
            int end = Math.min(start + pageable.getPageSize(), all.size());
            return new PageImpl<>(all.subList(start, end), pageable, all.size());
        }

        @Override
        public Page<OrderRecord> findByStatus(OrderStatus status, Pageable pageable) {
            List<OrderRecord> filtered = findByStatus(status);
            int start = (int) Math.min((long) pageable.getPageNumber() * pageable.getPageSize(), filtered.size());
            int end = Math.min(start + pageable.getPageSize(), filtered.size());
            return new PageImpl<>(filtered.subList(start, end), pageable, filtered.size());
        }

        @Override
        public Optional<OrderRecord> findByIdempotencyKey(String idempotencyKey) {
            UUID id = byIdempotencyKey.get(idempotencyKey);
            return id == null ? Optional.empty() : findById(id);
        }

        int size() {
            return byId.size();
        }
    }

    private static final class TestGlobalIdempotencyService extends GlobalIdempotencyService {
        private final Map<String, IdempotencyState> states = new ConcurrentHashMap<>();

        TestGlobalIdempotencyService() {
            super(mock(org.springframework.data.redis.core.StringRedisTemplate.class), true, 60, 3600);
        }

        @Override
        public IdempotencyState resolveState(String idempotencyKey) {
            return states.getOrDefault(idempotencyKey, new IdempotencyState(IdempotencyStatus.ABSENT, null, null));
        }

        @Override
        public boolean markInProgress(String idempotencyKey) {
            return states.putIfAbsent(
                    idempotencyKey,
                    new IdempotencyState(IdempotencyStatus.IN_PROGRESS, null, Instant.now())) == null;
        }

        @Override
        public void markCompleted(String idempotencyKey, UUID orderId) {
            if (idempotencyKey == null || orderId == null) {
                return;
            }
            states.put(idempotencyKey, new IdempotencyState(IdempotencyStatus.COMPLETED, orderId, Instant.now()));
        }

        void forceInProgress(String idempotencyKey) {
            states.put(idempotencyKey, new IdempotencyState(IdempotencyStatus.IN_PROGRESS, null, Instant.now()));
        }

        void clear(String idempotencyKey) {
            states.remove(idempotencyKey);
        }
    }
}
