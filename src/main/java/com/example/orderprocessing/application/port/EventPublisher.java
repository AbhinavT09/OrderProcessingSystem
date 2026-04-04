package com.example.orderprocessing.application.port;

import com.example.orderprocessing.application.event.OrderCreatedEvent;
import java.util.concurrent.CompletableFuture;

/**
 * Application port for publishing integration events.
 *
 * <p>Implemented by infrastructure messaging adapters (Kafka) and consumed by outbox processor
 * to keep write-path reliability concerns outside core application logic.</p>
 */
public interface EventPublisher {
    /**
     * Publishes a committed order-created event asynchronously.
     *
     * @param event event payload derived from durable order state
     * @return future that completes when publication succeeds or fails
     */
    CompletableFuture<Void> publishOrderCreated(OrderCreatedEvent event);
}
