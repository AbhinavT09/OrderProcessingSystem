package com.example.orderprocessing.application.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
/**
 * Immutable integration event payload for the {@code ORDER_CREATED} contract.
 *
 * <p><b>Architecture role:</b> application-layer event contract consumed by infrastructure adapters
 * (outbox publisher and Kafka consumer schema registry).</p>
 *
 * <p><b>Idempotency and resilience context:</b> {@code eventId} is the deduplication key used by
 * the consumer path; schema version allows forward-compatible parsing.</p>
 *
 * <p><b>Transaction boundary:</b> this type itself is transaction-neutral. It can be serialized
 * inside the DB transaction (outbox write) and later published inside a Kafka transaction by
 * producer adapters.</p>
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
        this(null, eventId, eventType, orderId, occurredAt);
    }
}
