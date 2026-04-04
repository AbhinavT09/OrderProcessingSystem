package com.example.orderprocessing.domain.state;

import com.example.orderprocessing.domain.model.OrderStatus;

/**
 * OrderStateFactory implements a concrete responsibility in the order processing service.
 * It is used to keep the boots the Spring runtime for the service layer explicit and maintainable in this architecture.
 */
public final class OrderStateFactory {

    /**
     * Creates a non-instantiable utility holder for state selection logic.
     */
    private OrderStateFactory() {
    }

    /**
     * Resolves the concrete state implementation for the persisted order status.
     * @param status status restored from storage that determines the state object to return
     * @return state implementation that enforces transitions for the given status
     */
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
