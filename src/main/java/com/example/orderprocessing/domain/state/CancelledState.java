package com.example.orderprocessing.domain.state;

import com.example.orderprocessing.domain.model.OrderStatus;
import java.util.Map;
import java.util.function.Supplier;

public class CancelledState extends AbstractOrderState {

    @Override
    public OrderStatus status() {
        return OrderStatus.CANCELLED;
    }

    @Override
    protected Map<OrderStatus, Supplier<OrderState>> transitions() {
        return Map.of();
    }
}
