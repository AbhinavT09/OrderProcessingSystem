package com.example.orderprocessing.domain.state;

import com.example.orderprocessing.domain.model.OrderStatus;
import java.util.Map;
import java.util.function.Supplier;

public class ProcessingState extends AbstractOrderState {

    private static final Map<OrderStatus, Supplier<OrderState>> TRANSITIONS = Map.of(
            OrderStatus.SHIPPED, ShippedState::new,
            OrderStatus.DELIVERED, DeliveredState::new
    );

    @Override
    public OrderStatus status() {
        return OrderStatus.PROCESSING;
    }

    @Override
    protected Map<OrderStatus, Supplier<OrderState>> transitions() {
        return TRANSITIONS;
    }
}
