package com.example.orderprocessing.domain.state;

import com.example.orderprocessing.domain.model.OrderStatus;

public final class OrderStateFactory {

    private OrderStateFactory() {
    }

    public static OrderState fromStatus(OrderStatus status) {
        return switch (status) {
            case PENDING -> new PendingState();
            case PROCESSING -> new ProcessingState();
            case SHIPPED -> new ShippedState();
            case DELIVERED -> new DeliveredState();
            case CANCELLED -> new CancelledState();
        };
    }
}
