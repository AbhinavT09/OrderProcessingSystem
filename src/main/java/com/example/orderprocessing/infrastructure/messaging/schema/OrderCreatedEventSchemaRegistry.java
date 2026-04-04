package com.example.orderprocessing.infrastructure.messaging.schema;

import com.example.orderprocessing.application.event.OrderCreatedEvent;

/**
 * OrderCreatedEventSchemaRegistry interface defines a stable boundary used by collaborating components.
 * It is used to keep the boots the Spring runtime for the service layer explicit and maintainable in this architecture.
 */
public interface OrderCreatedEventSchemaRegistry {
    /**
     * Performs latestSchemaVersion.
     * @return operation result
     */
    int latestSchemaVersion();

    /**
     * Performs serialize.
     * @param event input argument used by this operation
     * @return operation result
     */
    String serialize(OrderCreatedEvent event);

    /**
     * Performs deserialize.
     * @param payload input argument used by this operation
     * @return operation result
     */
    OrderCreatedEvent deserialize(String payload);

    /**
     * Performs validate.
     * @param event input argument used by this operation
     */
    void validate(OrderCreatedEvent event);
}
