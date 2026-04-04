package com.example.orderprocessing.application.service;

import com.example.orderprocessing.application.event.OrderCreatedEvent;
import com.example.orderprocessing.application.exception.InfrastructureException;
import com.example.orderprocessing.application.port.OutboxRepository;
import com.example.orderprocessing.infrastructure.persistence.entity.OutboxEntity;
import com.example.orderprocessing.infrastructure.persistence.entity.OutboxStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

@Service
public class OutboxService {

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public OutboxService(OutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    public void enqueueOrderCreated(String orderId, OrderCreatedEvent event) {
        OutboxEntity outbox = new OutboxEntity();
        outbox.setAggregateType("ORDER");
        outbox.setAggregateId(orderId);
        outbox.setEventType("OrderCreated");
        outbox.setPayload(toJson(event));
        outbox.setStatus(OutboxStatus.PENDING);
        outbox.setRetryCount(0);
        outboxRepository.save(outbox);
    }

    private String toJson(OrderCreatedEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            throw new InfrastructureException("Failed to serialize OrderCreatedEvent for outbox", ex);
        }
    }
}
