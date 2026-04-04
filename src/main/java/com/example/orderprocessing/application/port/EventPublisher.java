package com.example.orderprocessing.application.port;

import com.example.orderprocessing.application.event.OrderCreatedEvent;

public interface EventPublisher {
    void publishOrderCreated(OrderCreatedEvent event);
}
