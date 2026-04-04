package com.example.orderprocessing.domain.order.state;

import com.example.orderprocessing.application.exception.ConflictException;
import com.example.orderprocessing.domain.order.OrderStatus;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Base domain implementation for shared state-transition behavior.
 *
 * <p>Centralizes transition-map resolution and default conflict/no-op policies so concrete
 * states only declare permitted moves.</p>
 */
abstract class AbstractOrderState implements OrderState {

    /**
     * Transitions the order to the requested target state when the transition is allowed.
     *
     * @param target target order status.
     * @return next {@link OrderState} implementation representing the target status.
     * @throws ConflictException when the transition is not allowed for the current state.
     */
    @Override
    public OrderState transitionTo(OrderStatus target) {
        return Optional.ofNullable(transitions().get(target))
                .map(Supplier::get)
                .orElseThrow(() -> new ConflictException(
                        "Invalid transition from " + status() + " to " + target));
    }

    /**
     * Cancels the order for states that support cancellation.
     *
     * <p>By default, cancellation is rejected. States that support cancellation should override
     * this method.</p>
     *
     * @return next {@link OrderState} after cancellation.
     * @throws ConflictException when cancellation is not supported by the current state.
     */
    @Override
    public OrderState cancel() {
        throw new ConflictException("Only PENDING orders can be cancelled");
    }

    /**
     * Promotes a pending order to processing when supported by the current state.
     *
     * <p>Default behavior is a no-op. States that support this promotion can override as needed.</p>
     *
     * @return resulting {@link OrderState} after promotion attempt.
     */
    @Override
    public OrderState promotePendingToProcessing() {
        return this;
    }

    /**
     * Returns the allowed transition mapping for the current state.
     *
     * @return map of target {@link OrderStatus} to state supplier.
     */
    protected abstract Map<OrderStatus, Supplier<OrderState>> transitions();
}
