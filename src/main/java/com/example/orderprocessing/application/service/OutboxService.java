package com.example.orderprocessing.application.service;

import com.example.orderprocessing.application.event.OrderCreatedEvent;
import com.example.orderprocessing.application.port.OutboxRepository;
import com.example.orderprocessing.infrastructure.messaging.schema.OrderCreatedEventSchemaRegistry;
import com.example.orderprocessing.infrastructure.persistence.entity.OutboxEntity;
import com.example.orderprocessing.infrastructure.persistence.entity.OutboxStatus;
import org.springframework.beans.factory.annotation.Value;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
/**
 * Application-layer helper for transactional outbox writes.
 *
 * <p>Used by command services to persist integration events in the same transactional boundary
 * as domain state changes, enabling reliable asynchronous delivery.</p>
 */
public class OutboxService {

    private final OutboxRepository outboxRepository;
    private final OrderCreatedEventSchemaRegistry schemaRegistry;
    private final int totalPartitions;

    /**
     * Creates the outbox write helper.
     * @param outboxRepository outbox persistence port
     * @param objectMapper JSON serializer for payloads
     * @param schemaRegistry schema registry for payload validation
     */
    public OutboxService(OutboxRepository outboxRepository,
                         OrderCreatedEventSchemaRegistry schemaRegistry,
                         @Value("${app.outbox.partition.total:64}") int totalPartitions) {
        this.outboxRepository = outboxRepository;
        this.schemaRegistry = schemaRegistry;
        this.totalPartitions = Math.max(1, totalPartitions);
    }

    /**
     * Enqueues an order-created integration event for asynchronous publication.
     *
     * <p>Validates/serializes payload through schema registry, assigns deterministic partition
     * key from aggregate id, and stores event as {@code PENDING} for publisher pickup.</p>
     *
     * @param orderId aggregate identifier used for ordering/partitioning
     * @param event event payload representing committed business change
     */
    public void enqueueOrderCreated(String orderId, OrderCreatedEvent event) {
        OutboxEntity outbox = new OutboxEntity();
        outbox.setAggregateType("ORDER");
        outbox.setAggregateId(orderId);
        outbox.setEventType("OrderCreated");
        outbox.setPayload(schemaRegistry.serialize(event));
        outbox.setStatus(OutboxStatus.PENDING);
        outbox.setRetryCount(0);
        outbox.setPartitionKey(Math.floorMod(orderId.hashCode(), totalPartitions));
        outbox.setNextAttemptAt(Instant.now());
        outboxRepository.save(outbox);
    }
}
