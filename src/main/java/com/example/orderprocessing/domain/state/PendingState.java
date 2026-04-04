package com.example.orderprocessing.domain.state;

import com.example.orderprocessing.domain.model.OrderStatus;
import java.util.Map;
import java.util.function.Supplier;

public class PendingState extends AbstractOrderState {

    private static final Map<OrderStatus, Supplier<OrderState>> TRANSITIONS = Map.of(
            OrderStatus.PROCESSING, ProcessingState::new,
            OrderStatus.SHIPPED, ShippedState::new,
            OrderStatus.DELIVERED, DeliveredState::new
    );

    @Override
    public OrderStatus status() {
        return OrderStatus.PENDING;
    }

    @Override
    public OrderState cancel() {
        return new CancelledState();
    }

    @Override
    public OrderState promotePendingToProcessing() {
        return new ProcessingState();
    }

    @Override
    protected Map<OrderStatus, Supplier<OrderState>> transitions() {
        return TRANSITIONS;
    }
}
