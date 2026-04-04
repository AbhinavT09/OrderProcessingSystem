package com.example.orderprocessing.infrastructure.messaging;

import com.example.orderprocessing.application.event.OrderCreatedEvent;
import com.example.orderprocessing.application.port.EventPublisher;
import com.example.orderprocessing.application.port.OutboxRepository;
import com.example.orderprocessing.infrastructure.messaging.schema.OrderCreatedEventSchemaRegistry;
import com.example.orderprocessing.infrastructure.persistence.entity.OutboxEntity;
import com.example.orderprocessing.infrastructure.persistence.entity.OutboxStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxProcessorTest {

    @Test
    void processBatchMarksEventSentOnAsyncSuccess() {
        EventPublisher publisher = mock(EventPublisher.class);
        OutboxRepository repository = mock(OutboxRepository.class);
        OutboxRetryHandler retryHandler = mock(OutboxRetryHandler.class);
        OrderCreatedEventSchemaRegistry schemaRegistry = mock(OrderCreatedEventSchemaRegistry.class);
        TransactionTemplate tx = new TransactionTemplate(new NoOpTransactionManager());
        SimpleMeterRegistry meter = new SimpleMeterRegistry();
        OutboxProcessor processor = new OutboxProcessor(
                publisher, repository, schemaRegistry, tx, retryHandler, meter, 30_000);

        OutboxEntity event = outbox("order-1", OutboxStatus.PENDING);
        OrderCreatedEvent parsed = new OrderCreatedEvent(2, "evt-1", "ORDER_CREATED", "order-1", Instant.now().toString());
        when(schemaRegistry.deserialize(any())).thenReturn(parsed);
        when(publisher.publishOrderCreated(any())).thenReturn(CompletableFuture.completedFuture(null));

        processor.processBatch(List.of(event));

        assertEquals(OutboxStatus.SENT, event.getStatus());
        verify(repository, times(2)).save(event); // leased + sent writes
        verify(retryHandler, never()).handleFailure(any(), any());
    }

    @Test
    void processBatchDelegatesToRetryHandlerOnAsyncFailure() {
        EventPublisher publisher = mock(EventPublisher.class);
        OutboxRepository repository = mock(OutboxRepository.class);
        OutboxRetryHandler retryHandler = mock(OutboxRetryHandler.class);
        OrderCreatedEventSchemaRegistry schemaRegistry = mock(OrderCreatedEventSchemaRegistry.class);
        TransactionTemplate tx = new TransactionTemplate(new NoOpTransactionManager());
        SimpleMeterRegistry meter = new SimpleMeterRegistry();
        OutboxProcessor processor = new OutboxProcessor(
                publisher, repository, schemaRegistry, tx, retryHandler, meter, 30_000);

        OutboxEntity event = outbox("order-2", OutboxStatus.PENDING);
        when(schemaRegistry.deserialize(any())).thenReturn(
                new OrderCreatedEvent(2, "evt-2", "ORDER_CREATED", "order-2", Instant.now().toString()));
        CompletableFuture<Void> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("kafka down"));
        when(publisher.publishOrderCreated(any())).thenReturn(failed);

        processor.processBatch(List.of(event));

        verify(retryHandler).handleFailure(any(), any());
    }

    private OutboxEntity outbox(String aggregateId, OutboxStatus status) {
        OutboxEntity e = new OutboxEntity();
        e.setId(UUID.randomUUID());
        e.setAggregateType("ORDER");
        e.setAggregateId(aggregateId);
        e.setEventType("OrderCreated");
        e.setPayload("{\"ok\":true}");
        e.setStatus(status);
        e.setRetryCount(0);
        e.setPartitionKey(0);
        e.setCreatedAt(Instant.now().minusSeconds(30));
        e.setNextAttemptAt(Instant.now());
        return e;
    }

    private static final class NoOpTransactionManager implements PlatformTransactionManager {
        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
        }

        @Override
        public void rollback(TransactionStatus status) {
        }
    }
}
