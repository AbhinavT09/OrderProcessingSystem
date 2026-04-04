package com.example.orderprocessing.domain.order.state;

import com.example.orderprocessing.domain.order.OrderStatus;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Terminal domain state for successfully delivered orders.
 */
public class DeliveredState extends AbstractOrderState {

    @Override
    /**
     * Returns {@code DELIVERED}.
     */
    public OrderStatus status() {
        return OrderStatus.DELIVERED;
    }

    @Override
    protected Map<OrderStatus, Supplier<OrderState>> transitions() {
        return Map.of();
    }
}
