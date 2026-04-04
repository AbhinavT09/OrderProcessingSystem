package com.example.orderprocessing.domain.order.state;

import com.example.orderprocessing.domain.order.OrderStatus;

/**
 * Domain state-pattern contract for order lifecycle behavior.
 *
 * <p>Each implementation encapsulates transitions allowed from a single lifecycle state.</p>
 */
public interface OrderState {

    /**
     * Exposes the status represented by this state object.
     *
     * @return current lifecycle status
     */
    OrderStatus status();

    /**
     * Attempts transition to a target status.
     *
     * @param target requested status
     * @return next state implementation
     */
    OrderState transitionTo(OrderStatus target);

    /**
     * Applies cancellation semantics for current state.
     *
     * @return resulting state after cancellation
     */
    OrderState cancel();

    /**
     * Promotes pending orders to processing when state supports it.
     *
     * @return resulting state after promotion attempt
     */
    OrderState promotePendingToProcessing();
}
