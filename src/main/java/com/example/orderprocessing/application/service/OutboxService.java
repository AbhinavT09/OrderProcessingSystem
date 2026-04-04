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
public class OutboxService {

    private final OutboxRepository outboxRepository;
    private final OrderCreatedEventSchemaRegistry schemaRegistry;
    private final int totalPartitions;

    public OutboxService(OutboxRepository outboxRepository,
                         OrderCreatedEventSchemaRegistry schemaRegistry,
                         @Value("${app.outbox.partition.total:64}") int totalPartitions) {
        this.outboxRepository = outboxRepository;
        this.schemaRegistry = schemaRegistry;
        this.totalPartitions = Math.max(1, totalPartitions);
    }

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
