package com.example.orderprocessing.domain.state;

import com.example.orderprocessing.domain.model.OrderStatus;
import java.util.Map;
import java.util.function.Supplier;

/**
 * DeliveredState implements a concrete responsibility in the order processing service.
 * It is used to keep the boots the Spring runtime for the service layer explicit and maintainable in this architecture.
 */
public class DeliveredState extends AbstractOrderState {

    @Override
    /**
     * Executes status.
     * @return operation result
     */
    public OrderStatus status() {
        return OrderStatus.DELIVERED;
    }

    @Override
    protected Map<OrderStatus, Supplier<OrderState>> transitions() {
        return Map.of();
    }
}
