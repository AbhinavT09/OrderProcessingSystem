package com.example.orderprocessing.application.service;

import com.example.orderprocessing.application.event.OrderCreatedEvent;
import com.example.orderprocessing.application.port.OutboxRepository;
import com.example.orderprocessing.infrastructure.messaging.schema.OrderCreatedEventSchemaRegistry;
import com.example.orderprocessing.infrastructure.persistence.entity.OutboxEntity;
import com.example.orderprocessing.infrastructure.persistence.entity.OutboxStatus;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxServiceTest {

    @Test
    void enqueueOrderCreatedPersistsPendingOutboxWithPartitionAndPayload() {
        OutboxRepository repository = mock(OutboxRepository.class);
        OrderCreatedEventSchemaRegistry schemaRegistry = mock(OrderCreatedEventSchemaRegistry.class);
        when(schemaRegistry.serialize(org.mockito.ArgumentMatchers.any())).thenReturn("{\"event\":\"ok\"}");
        OutboxService service = new OutboxService(repository, schemaRegistry, 64);

        String orderId = "order-123";
        OrderCreatedEvent event = new OrderCreatedEvent(2, "evt-1", "ORDER_CREATED", orderId, Instant.now().toString());
        service.enqueueOrderCreated(orderId, event);

        ArgumentCaptor<OutboxEntity> captor = ArgumentCaptor.forClass(OutboxEntity.class);
        verify(repository).save(captor.capture());
        OutboxEntity saved = captor.getValue();

        assertEquals("ORDER", saved.getAggregateType());
        assertEquals(orderId, saved.getAggregateId());
        assertEquals("OrderCreated", saved.getEventType());
        assertEquals("{\"event\":\"ok\"}", saved.getPayload());
        assertEquals(OutboxStatus.PENDING, saved.getStatus());
        assertEquals(0, saved.getRetryCount());
        assertEquals(Math.floorMod(orderId.hashCode(), 64), saved.getPartitionKey());
        assertNotNull(saved.getNextAttemptAt());
    }
}
