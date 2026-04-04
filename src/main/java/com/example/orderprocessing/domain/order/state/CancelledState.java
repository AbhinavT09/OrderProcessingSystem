package com.example.orderprocessing.domain.order.state;

import com.example.orderprocessing.domain.order.OrderStatus;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Terminal domain state for cancelled orders.
 */
public class CancelledState extends AbstractOrderState {

    @Override
    /**
     * Returns {@code CANCELLED}.
     */
    public OrderStatus status() {
        return OrderStatus.CANCELLED;
    }

    @Override
    protected Map<OrderStatus, Supplier<OrderState>> transitions() {
        return Map.of();
    }
}
