package com.example.orderprocessing.application.port;

import com.example.orderprocessing.application.event.OrderCreatedEvent;
import java.util.concurrent.CompletableFuture;

/**
 * EventPublisher interface defines a stable boundary used by collaborating components.
 * It is used to keep the boots the Spring runtime for the service layer explicit and maintainable in this architecture.
 */
public interface EventPublisher {
    /**
     * Performs publishOrderCreated.
     * @param event input argument used by this operation
     * @return operation result
     */
    CompletableFuture<Void> publishOrderCreated(OrderCreatedEvent event);
}
