package com.example.orderprocessing.domain.state;

import com.example.orderprocessing.domain.model.OrderStatus;
import java.util.Map;
import java.util.function.Supplier;

/**
 * PendingState implements a concrete responsibility in the order processing service.
 * It is used to keep the boots the Spring runtime for the service layer explicit and maintainable in this architecture.
 */
public class PendingState extends AbstractOrderState {

    private static final Map<OrderStatus, Supplier<OrderState>> TRANSITIONS = Map.of(
            OrderStatus.PROCESSING, ProcessingState::new,
            OrderStatus.SHIPPED, ShippedState::new,
            OrderStatus.DELIVERED, DeliveredState::new
    );

    @Override
    /**
     * Executes status.
     * @return operation result
     */
    public OrderStatus status() {
        return OrderStatus.PENDING;
    }

    @Override
    /**
     * Executes cancel.
     * @return operation result
     */
    public OrderState cancel() {
        return new CancelledState();
    }

    @Override
    /**
     * Executes promotePendingToProcessing.
     * @return operation result
     */
    public OrderState promotePendingToProcessing() {
        return new ProcessingState();
    }

    @Override
    protected Map<OrderStatus, Supplier<OrderState>> transitions() {
        return TRANSITIONS;
    }
}
