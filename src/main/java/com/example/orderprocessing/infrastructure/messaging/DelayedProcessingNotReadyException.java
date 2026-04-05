package com.example.orderprocessing.infrastructure.messaging;

/**
 * Signal that an event is valid but not yet eligible for processing.
 *
 * <p><b>Architecture role:</b> infrastructure consumer exception that intentionally routes to
 * retry-topic delays.</p>
 *
 * <p><b>Resilience context:</b> enables temporal decoupling without thread blocking; the consumer
 * fails fast and lets Kafka retry scheduling re-drive the event.</p>
 */
public class DelayedProcessingNotReadyException extends RuntimeException {
    /**
     * Creates an exception for events that are not due yet.
     * @param message scheduling context message
     */
    public DelayedProcessingNotReadyException(String message) {
        super(message);
    }
}
