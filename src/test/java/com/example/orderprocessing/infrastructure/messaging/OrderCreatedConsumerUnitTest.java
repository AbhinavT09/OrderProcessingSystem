package com.example.orderprocessing.infrastructure.messaging;

import com.example.orderprocessing.application.port.OrderRepository;
import com.example.orderprocessing.application.port.ProcessedEventRepository;
import com.example.orderprocessing.application.service.OrderMapper;
import com.example.orderprocessing.domain.model.OrderStatus;
import com.example.orderprocessing.infrastructure.persistence.entity.OrderEntity;
import com.example.orderprocessing.infrastructure.persistence.entity.OrderItemEmbeddable;
import com.example.orderprocessing.infrastructure.persistence.entity.ProcessedEventEntity;
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
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderCreatedConsumerUnitTest {

    @Test
    void shouldPromotePendingOrderAndPersistProcessedMarker() throws Exception {
        UUID orderId = UUID.randomUUID();
        InMemoryOrderRepository orderRepository = new InMemoryOrderRepository();
        orderRepository.save(buildOrder(orderId, OrderStatus.PENDING));

        InMemoryProcessedEventRepository processedRepository = new InMemoryProcessedEventRepository();
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        OrderCreatedConsumer consumer = new OrderCreatedConsumer(
                new VersionedJsonOrderCreatedEventSchemaRegistry(
                        new ObjectMapper(),
                        meterRegistry),
                orderRepository,
                processedRepository,
                new OrderMapper(),
                meterRegistry,
                new TransactionTemplate(new NoopTransactionManager()));

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

        assertEquals(OrderStatus.PROCESSING, orderRepository.findById(orderId).orElseThrow().getStatus());
        assertEquals(1, processedRepository.savedCount());
    }

    private static OrderEntity buildOrder(UUID id, OrderStatus status) {
        OrderEntity entity = new OrderEntity();
        entity.setId(id);
        entity.setStatus(status);
        entity.setCreatedAt(Instant.now().minus(10, ChronoUnit.MINUTES));
        entity.setVersion(0L);

        OrderItemEmbeddable item = new OrderItemEmbeddable();
        item.setProductName("Item");
        item.setQuantity(1);
        item.setPrice(10.0);
        entity.setItems(List.of(item));
        return entity;
    }

    private static final class InMemoryOrderRepository implements OrderRepository {
        private final Map<UUID, OrderEntity> store = new HashMap<>();

        @Override
        public OrderEntity save(OrderEntity order) {
            store.put(order.getId(), order);
            return order;
        }

        @Override
        public Optional<OrderEntity> findById(UUID id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public List<OrderEntity> findAll() {
            return new ArrayList<>(store.values());
        }

        @Override
        public List<OrderEntity> findByStatus(OrderStatus status) {
            return store.values().stream().filter(o -> o.getStatus() == status).toList();
        }

        @Override
        public Optional<OrderEntity> findByIdempotencyKey(String idempotencyKey) {
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
        public ProcessedEventEntity save(ProcessedEventEntity event) {
            processed.add(event);
            return event;
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
