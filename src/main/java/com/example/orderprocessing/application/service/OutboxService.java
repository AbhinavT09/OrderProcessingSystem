package com.example.orderprocessing.application.service;

import com.example.orderprocessing.application.port.OutboxRepositoryPort;
import com.example.orderprocessing.domain.model.OutboxStatus;
import com.example.orderprocessing.infrastructure.persistence.entity.OutboxEventEntity;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class OutboxService {

    private static final Logger log = LoggerFactory.getLogger(OutboxService.class);

    private final OutboxRepositoryPort outboxRepository;
    private final int maxAttempts;

    public OutboxService(OutboxRepositoryPort outboxRepository,
                         @Value("${app.outbox.max-attempts:8}") int maxAttempts) {
        this.outboxRepository = outboxRepository;
        this.maxAttempts = maxAttempts;
    }

    public void enqueue(String aggregateType, String aggregateId, String eventType, String payload) {
        OutboxEventEntity event = new OutboxEventEntity();
        event.setEventId(UUID.randomUUID());
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setEventType(eventType);
        event.setPayload(payload);
        event.setStatus(OutboxStatus.PENDING);
        event.setAttempts(0);
        event.setMaxAttempts(maxAttempts);
        event.setNextAttemptAt(Instant.now());
        event.setCreatedAt(Instant.now());
        outboxRepository.save(event);

        log.info("Outbox event queued eventId={} aggregateType={} aggregateId={} eventType={}",
                event.getEventId(), aggregateType, aggregateId, eventType);
    }
}
