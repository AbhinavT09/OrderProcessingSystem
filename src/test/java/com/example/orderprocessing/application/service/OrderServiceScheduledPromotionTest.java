package com.example.orderprocessing.application.service;

import com.example.orderprocessing.application.port.CacheProvider;
import com.example.orderprocessing.application.port.OrderItemRecord;
import com.example.orderprocessing.application.port.OrderRecord;
import com.example.orderprocessing.application.port.OrderRepository;
import com.example.orderprocessing.domain.order.OrderStatus;
import com.example.orderprocessing.infrastructure.crosscutting.GlobalIdempotencyService;
import com.example.orderprocessing.infrastructure.resilience.BackpressureManager;
import com.example.orderprocessing.infrastructure.resilience.RegionalConsistencyManager;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrderServiceScheduledPromotionTest {

    @Test
    void promotePendingOrdersScheduled_movesAllPendingToProcessing() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        InMemoryOrderRepository repository = new InMemoryOrderRepository();
        repository.save(buildOrder(id1, OrderStatus.PENDING));
        repository.save(buildOrder(id2, OrderStatus.PENDING));

        OrderService service = buildService(repository);

        service.promotePendingOrdersScheduled();

        assertEquals(OrderStatus.PROCESSING, repository.findById(id1).orElseThrow().status());
        assertEquals(OrderStatus.PROCESSING, repository.findById(id2).orElseThrow().status());
    }

    private static OrderService buildService(InMemoryOrderRepository repository) {
        CacheProvider cacheProvider = new NoOpCacheProvider();
        OutboxService outbox = mock(OutboxService.class);
        OrderMapper mapper = new OrderMapper();
        RegionalConsistencyManager regionalConsistencyManager = mock(RegionalConsistencyManager.class);
        BackpressureManager backpressureManager = mock(BackpressureManager.class);
        GlobalIdempotencyService idempotency = mock(GlobalIdempotencyService.class);
        when(regionalConsistencyManager.allowsWrites()).thenReturn(true);
        when(regionalConsistencyManager.regionId()).thenReturn("region-a");
        when(regionalConsistencyManager.shouldApplyIncomingUpdate(any(), any(), any(), any())).thenReturn(true);
        when(backpressureManager.shouldRejectWrites()).thenReturn(false);
        return new OrderService(
                repository,
                cacheProvider,
                outbox,
                mapper,
                idempotency,
                regionalConsistencyManager,
                backpressureManager,
                new TransactionTemplate(new NoopTransactionManager()),
                new SimpleMeterRegistry());
    }

    private static OrderRecord buildOrder(UUID id, OrderStatus status) {
        return new OrderRecord(
                id,
                0L,
                status,
                Instant.now().minus(10, ChronoUnit.MINUTES),
                null,
                "test-owner",
                "region-a",
                Instant.now().minus(10, ChronoUnit.MINUTES),
                List.of(new OrderItemRecord("Item", 1, 10.0)));
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
        private final Map<UUID, OrderRecord> store = new HashMap<>();

        @Override
        public OrderRecord save(OrderRecord order) {
            long nextVersion = order.version() == null ? 0L : order.version() + 1;
            OrderRecord withVersion = new OrderRecord(
                    order.id(),
                    nextVersion,
                    order.status(),
                    order.createdAt(),
                    order.idempotencyKey(),
                    order.ownerSubject(),
                    order.regionId(),
                    order.lastUpdatedTimestamp(),
                    order.items());
            store.put(order.id(), withVersion);
            return withVersion;
        }

        @Override
        public Optional<OrderRecord> findById(UUID id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public List<OrderRecord> findAll() {
            return new ArrayList<>(store.values());
        }

        @Override
        public List<OrderRecord> findByStatus(OrderStatus status) {
            return store.values().stream().filter(o -> o.status() == status).toList();
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
            return Optional.empty();
        }

        @Override
        public Optional<OrderRecord> findByIdAndOwnerSubject(UUID id, String ownerSubject) {
            return findById(id).filter(o -> ownerSubject != null && ownerSubject.equals(o.ownerSubject()));
        }

        @Override
        public Page<OrderRecord> findByOwnerSubject(String ownerSubject, Pageable pageable) {
            List<OrderRecord> filtered = store.values().stream()
                    .filter(o -> ownerSubject != null && ownerSubject.equals(o.ownerSubject()))
                    .sorted(Comparator.comparing(OrderRecord::createdAt))
                    .toList();
            int start = (int) Math.min((long) pageable.getPageNumber() * pageable.getPageSize(), filtered.size());
            int end = Math.min(start + pageable.getPageSize(), filtered.size());
            return new PageImpl<>(filtered.subList(start, end), pageable, filtered.size());
        }

        @Override
        public Page<OrderRecord> findByOwnerSubjectAndStatus(String ownerSubject, OrderStatus status, Pageable pageable) {
            List<OrderRecord> filtered = store.values().stream()
                    .filter(o -> ownerSubject != null && ownerSubject.equals(o.ownerSubject()) && status == o.status())
                    .sorted(Comparator.comparing(OrderRecord::createdAt))
                    .toList();
            int start = (int) Math.min((long) pageable.getPageNumber() * pageable.getPageSize(), filtered.size());
            int end = Math.min(start + pageable.getPageSize(), filtered.size());
            return new PageImpl<>(filtered.subList(start, end), pageable, filtered.size());
        }
    }

    private static final class NoopTransactionManager implements PlatformTransactionManager {
        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
            // no-op
        }

        @Override
        public void rollback(TransactionStatus status) {
            // no-op
        }
    }
}
