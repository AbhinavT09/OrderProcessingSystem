package com.example.orderprocessing.domain.state;

import com.example.orderprocessing.domain.model.OrderStatus;
import java.util.Map;
import java.util.function.Supplier;

public class ShippedState extends AbstractOrderState {

    private static final Map<OrderStatus, Supplier<OrderState>> TRANSITIONS = Map.of(
            OrderStatus.DELIVERED, DeliveredState::new
    );

    @Override
    public OrderStatus status() {
        return OrderStatus.SHIPPED;
    }

    @Override
    protected Map<OrderStatus, Supplier<OrderState>> transitions() {
        return TRANSITIONS;
    }
}
