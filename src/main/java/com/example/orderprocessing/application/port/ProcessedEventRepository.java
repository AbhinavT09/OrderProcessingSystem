package com.example.orderprocessing.application.port;

import java.time.Instant;

/**
 * Hexagonal application port for processed-event dedup markers.
 *
 * <p>Keeps consumer idempotency logic independent of persistence entities.</p>
 *
 * <p><b>Idempotency context:</b> this port is the exactly-once guardrail for consumers operating
 * in at-least-once Kafka delivery environments.</p>
 */
public interface ProcessedEventRepository {
    /**
     * Checks whether an event id has already been applied.
     *
     * @param eventId consumed event identifier
     * @return true when a dedup marker already exists
     */
    boolean existsByEventId(String eventId);
    /**
     * Persists a processed marker for a consumed event.
     *
     * @param eventId consumed event identifier
     * @param eventType consumed event type
     * @param processedAt marker timestamp
     */
    void save(String eventId, String eventType, Instant processedAt);
}
