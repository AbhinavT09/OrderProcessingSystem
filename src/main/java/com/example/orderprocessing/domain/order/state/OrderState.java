package com.example.orderprocessing.domain.order.state;

import com.example.orderprocessing.domain.order.OrderStatus;

/**
 * OrderState interface defines a stable boundary used by collaborating components.
 * It is used to keep the boots the Spring runtime for the service layer explicit and maintainable in this architecture.
 */
public interface OrderState {

    /**
     * Performs status.
     * @return operation result
     */
    OrderStatus status();

    /**
     * Performs transitionTo.
     * @param target input argument used by this operation
     * @return operation result
     */
    OrderState transitionTo(OrderStatus target);

    /**
     * Performs cancel.
     * @return operation result
     */
    OrderState cancel();

    /**
     * Performs promotePendingToProcessing.
     * @return operation result
     */
    OrderState promotePendingToProcessing();
}
