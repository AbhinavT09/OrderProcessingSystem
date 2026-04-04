package com.example.orderprocessing.domain.state;

import com.example.orderprocessing.domain.model.OrderStatus;
import java.util.Map;
import java.util.function.Supplier;

/**
 * ShippedState implements a concrete responsibility in the order processing service.
 * It is used to keep the boots the Spring runtime for the service layer explicit and maintainable in this architecture.
 */
public class ShippedState extends AbstractOrderState {

    private static final Map<OrderStatus, Supplier<OrderState>> TRANSITIONS = Map.of(
            OrderStatus.DELIVERED, DeliveredState::new
    );

    @Override
    /**
     * Executes status.
     * @return operation result
     */
    public OrderStatus status() {
        return OrderStatus.SHIPPED;
    }

    @Override
    protected Map<OrderStatus, Supplier<OrderState>> transitions() {
        return TRANSITIONS;
    }
}
