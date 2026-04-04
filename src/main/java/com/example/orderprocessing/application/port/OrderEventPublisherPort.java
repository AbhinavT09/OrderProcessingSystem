package com.example.orderprocessing.application.port;

import com.example.orderprocessing.infrastructure.persistence.entity.OutboxEventEntity;

public interface OrderEventPublisherPort {
    void publish(OutboxEventEntity event);
}
