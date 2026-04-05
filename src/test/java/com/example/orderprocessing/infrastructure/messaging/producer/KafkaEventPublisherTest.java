package com.example.orderprocessing.infrastructure.messaging.producer;

import com.example.orderprocessing.application.event.OrderCreatedEvent;
import com.example.orderprocessing.application.exception.InfrastructureException;
import com.example.orderprocessing.infrastructure.messaging.schema.OrderCreatedEventSchemaRegistry;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KafkaEventPublisherTest {

    @SuppressWarnings("unchecked")
    @Test
    void publishOrderCreatedCompletesOnAsyncSuccess() {
        KafkaTemplate<String, String> template = mock(KafkaTemplate.class);
        OrderCreatedEventSchemaRegistry schemaRegistry = mock(OrderCreatedEventSchemaRegistry.class);
        when(schemaRegistry.serialize(any())).thenReturn("{\"event\":\"ok\"}");
        CompletableFuture<SendResult<String, String>> sendFuture = new CompletableFuture<>();
        sendFuture.complete(mock(SendResult.class));
        when(template.executeInTransaction(any())).thenReturn(sendFuture);

        KafkaEventPublisher publisher = new KafkaEventPublisher(template, schemaRegistry, "order.events", 2, 30);
        CompletableFuture<Void> result = publisher.publishOrderCreated(
                new OrderCreatedEvent(2, "evt-1", "ORDER_CREATED", "order-1", "2026-01-01T00:00:00Z"));

        assertTrue(result.isDone());
        assertTrue(!result.isCompletedExceptionally());
    }

    @SuppressWarnings("unchecked")
    @Test
    void publishOrderCreatedOpensCircuitAfterConsecutiveFailures() {
        KafkaTemplate<String, String> template = mock(KafkaTemplate.class);
        OrderCreatedEventSchemaRegistry schemaRegistry = mock(OrderCreatedEventSchemaRegistry.class);
        when(schemaRegistry.serialize(any())).thenReturn("{\"event\":\"ok\"}");
        CompletableFuture<SendResult<String, String>> failed1 = new CompletableFuture<>();
        failed1.completeExceptionally(new RuntimeException("kafka down 1"));
        CompletableFuture<SendResult<String, String>> failed2 = new CompletableFuture<>();
        failed2.completeExceptionally(new RuntimeException("kafka down 2"));
        when(template.executeInTransaction(any()))
                .thenReturn(failed1)
                .thenReturn(failed2);

        KafkaEventPublisher publisher = new KafkaEventPublisher(template, schemaRegistry, "order.events", 2, 30);

        CompletableFuture<Void> first = publisher.publishOrderCreated(
                new OrderCreatedEvent(2, "evt-1", "ORDER_CREATED", "order-1", "2026-01-01T00:00:00Z"));
        CompletableFuture<Void> second = publisher.publishOrderCreated(
                new OrderCreatedEvent(2, "evt-2", "ORDER_CREATED", "order-2", "2026-01-01T00:00:00Z"));
        CompletableFuture<Void> third = publisher.publishOrderCreated(
                new OrderCreatedEvent(2, "evt-3", "ORDER_CREATED", "order-3", "2026-01-01T00:00:00Z"));

        assertTrue(first.isCompletedExceptionally());
        assertTrue(second.isCompletedExceptionally());
        assertTrue(third.isCompletedExceptionally());
        Throwable cause = third.handle((ok, ex) -> ex).join();
        assertTrue(cause.getCause() instanceof InfrastructureException || cause instanceof InfrastructureException);
    }
}
