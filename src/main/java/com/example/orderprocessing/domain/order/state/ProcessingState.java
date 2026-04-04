package com.example.orderprocessing.domain.order.state;

import com.example.orderprocessing.domain.order.OrderStatus;
import java.util.Map;
import java.util.function.Supplier;

/**
 * ProcessingState implements a concrete responsibility in the order processing service.
 * It is used to keep the boots the Spring runtime for the service layer explicit and maintainable in this architecture.
 */
public class ProcessingState extends AbstractOrderState {

    private static final Map<OrderStatus, Supplier<OrderState>> TRANSITIONS = Map.of(
            OrderStatus.SHIPPED, ShippedState::new,
            OrderStatus.DELIVERED, DeliveredState::new
    );

    @Override
    /**
     * Executes status.
     * @return operation result
     */
    public OrderStatus status() {
        return OrderStatus.PROCESSING;
    }

    @Override
    protected Map<OrderStatus, Supplier<OrderState>> transitions() {
        return TRANSITIONS;
    }
}
