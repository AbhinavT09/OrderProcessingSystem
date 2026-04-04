package com.example.orderprocessing.domain.order.state;

import com.example.orderprocessing.domain.order.OrderStatus;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Domain state representing newly created orders awaiting processing.
 */
public class PendingState extends AbstractOrderState {

    private static final Map<OrderStatus, Supplier<OrderState>> TRANSITIONS = Map.of(
            OrderStatus.PROCESSING, ProcessingState::new,
            OrderStatus.SHIPPED, ShippedState::new,
            OrderStatus.DELIVERED, DeliveredState::new
    );

    @Override
    /**
     * Returns {@code PENDING}.
     */
    public OrderStatus status() {
        return OrderStatus.PENDING;
    }

    @Override
    /**
     * Allows cancellation directly from pending state.
     */
    public OrderState cancel() {
        return new CancelledState();
    }

    @Override
    /**
     * Advances pending order to processing state.
     */
    public OrderState promotePendingToProcessing() {
        return new ProcessingState();
    }

    @Override
    protected Map<OrderStatus, Supplier<OrderState>> transitions() {
        return TRANSITIONS;
    }
}
