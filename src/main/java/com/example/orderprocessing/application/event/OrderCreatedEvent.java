package com.example.orderprocessing.application.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
/**
 * OrderCreatedEvent record captures immutable data transferred between layers.
 * It is used to keep the boots the Spring runtime for the service layer explicit and maintainable in this architecture.
 */
public record OrderCreatedEvent(
        Integer schemaVersion,
        String eventId,
        String eventType,
        String orderId,
        String occurredAt
) {
    /**
     * Creates an event using the latest schema version.
     * @param eventId unique event identifier
     * @param eventType event type name
     * @param orderId aggregate identifier
     * @param occurredAt ISO-8601 timestamp for event occurrence
     */
    public OrderCreatedEvent(String eventId, String eventType, String orderId, String occurredAt) {
        this(2, eventId, eventType, orderId, occurredAt);
    }
}
