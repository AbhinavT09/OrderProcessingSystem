package com.example.orderprocessing.application.event;

public record OrderCreatedEvent(
        String eventId,
        String eventType,
        String orderId,
        String occurredAt
) {
}
