package com.example.orderprocessing.domain.order.state;

import com.example.orderprocessing.domain.order.OrderStatus;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Domain state for orders that have left fulfillment and are in transit.
 */
public class ShippedState extends AbstractOrderState {

    private static final Map<OrderStatus, Supplier<OrderState>> TRANSITIONS = Map.of(
            OrderStatus.DELIVERED, DeliveredState::new
    );

    @Override
    /**
     * Returns {@code SHIPPED}.
     */
    public OrderStatus status() {
        return OrderStatus.SHIPPED;
    }

    @Override
    protected Map<OrderStatus, Supplier<OrderState>> transitions() {
        return TRANSITIONS;
    }
}
