package com.example.orderprocessing.application.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderCreatedEvent(
        Integer schemaVersion,
        String eventId,
        String eventType,
        String orderId,
        String occurredAt
) {
    public OrderCreatedEvent(String eventId, String eventType, String orderId, String occurredAt) {
        this(2, eventId, eventType, orderId, occurredAt);
    }
}
