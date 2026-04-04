package com.example.orderprocessing.infrastructure.messaging.schema;

import com.example.orderprocessing.application.event.OrderCreatedEvent;

public interface OrderCreatedEventSchemaRegistry {
    int latestSchemaVersion();

    String serialize(OrderCreatedEvent event);

    OrderCreatedEvent deserialize(String payload);

    void validate(OrderCreatedEvent event);
}
