package com.example.orderprocessing.domain.state;

import com.example.orderprocessing.domain.model.OrderStatus;

public interface OrderState {

    OrderStatus status();

    OrderState transitionTo(OrderStatus target);

    OrderState cancel();

    OrderState promotePendingToProcessing();
}
