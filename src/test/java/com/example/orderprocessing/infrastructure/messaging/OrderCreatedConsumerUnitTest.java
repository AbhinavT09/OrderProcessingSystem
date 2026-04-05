package com.example.orderprocessing.infrastructure.messaging;

import com.example.orderprocessing.application.port.OrderItemRecord;
import com.example.orderprocessing.application.port.OrderRecord;
import com.example.orderprocessing.application.port.OrderRepository;
import com.example.orderprocessing.application.port.ProcessedEventRepository;
import com.example.orderprocessing.application.service.OrderMapper;
import com.example.orderprocessing.domain.order.OrderStatus;
import com.example.orderprocessing.infrastructure.messaging.consumer.OrderCreatedConsumer;
import com.example.orderprocessing.infrastructure.persistence.entity.ProcessedEventEntity;
import com.example.orderprocessing.infrastructure.resilience.BackpressureManager;
import com.example.orderprocessing.infrastructure.resilience.RegionalConsistencyManager;
import com.example.orderprocessing.infrastructure.messaging.schema.VersionedJsonOrderCreatedEventSchemaRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrderCreatedConsumerUnitTest {

    @Test
    void shouldPromotePendingOrderAndPersistProcessedMarker() throws Exception {
        UUID orderId = UUID.randomUUID();
        InMemoryOrderRepository orderRepository = new InMemoryOrderRepository();
        orderRepository.save(buildOrder(orderId, OrderStatus.PENDING));

        InMemoryProcessedEventRepository processedRepository = new InMemoryProcessedEventRepository();
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        BackpressureManager backpressureManager = mock(BackpressureManager.class);
        RegionalConsistencyManager regionalConsistencyManager = mock(RegionalConsistencyManager.class);
        when(regionalConsistencyManager.shouldApplyIncomingUpdate(any(), any(), any(), any())).thenReturn(true);
        OrderCreatedConsumer consumer = new OrderCreatedConsumer(
                new VersionedJsonOrderCreatedEventSchemaRegistry(
                        new ObjectMapper(),
                        meterRegistry),
                orderRepository,
                processedRepository,
                new OrderMapper(),
                meterRegistry,
                new TransactionTemplate(new NoopTransactionManager()),
                backpressureManager,
                regionalConsistencyManager,
                300_000L);

        String payload = """
                {
                  "eventId": "evt-promote",
                  "eventType": "ORDER_CREATED",
                  "orderId": "%s",
                  "occurredAt": "%s"
                }
                """.formatted(orderId, Instant.now().minus(6, ChronoUnit.MINUTES));

        Acknowledgment ack = () -> { };
        consumer.consume(payload, ack);

        assertEquals(OrderStatus.PROCESSING, orderRepository.findById(orderId).orElseThrow().status());
        assertEquals(1, processedRepository.savedCount());
    }

    private static OrderRecord buildOrder(UUID id, OrderStatus status) {
        return new OrderRecord(
                id,
                0L,
                status,
                Instant.now().minus(10, ChronoUnit.MINUTES),
                null,
                "region-a",
                Instant.now().minus(10, ChronoUnit.MINUTES),
                List.of(new OrderItemRecord("Item", 1, 10.0)));
    }

    private static final class InMemoryOrderRepository implements OrderRepository {
        private final Map<UUID, OrderRecord> store = new HashMap<>();

        @Override
        public OrderRecord save(OrderRecord order) {
            store.put(order.id(), order);
            return order;
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
    }

    private static final class InMemoryProcessedEventRepository implements ProcessedEventRepository {
        private final List<ProcessedEventEntity> processed = new ArrayList<>();

        @Override
        public boolean existsByEventId(String eventId) {
            return processed.stream().anyMatch(e -> eventId.equals(e.getEventId()));
        }

        @Override
        public void save(String eventId, String eventType, Instant processedAt) {
            ProcessedEventEntity event = new ProcessedEventEntity();
            event.setEventId(eventId);
            event.setEventType(eventType);
            event.setProcessedAt(processedAt);
            processed.add(event);
        }

        int savedCount() {
            return processed.size();
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
