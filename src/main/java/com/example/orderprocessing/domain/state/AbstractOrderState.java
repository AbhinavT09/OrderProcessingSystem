package com.example.orderprocessing.domain.state;

import com.example.orderprocessing.application.exception.ConflictException;
import com.example.orderprocessing.domain.model.OrderStatus;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

abstract class AbstractOrderState implements OrderState {

    @Override
    public OrderState transitionTo(OrderStatus target) {
        return Optional.ofNullable(transitions().get(target))
                .map(Supplier::get)
                .orElseThrow(() -> new ConflictException(
                        "Invalid transition from " + status() + " to " + target));
    }

    @Override
    public OrderState cancel() {
        throw new ConflictException("Only PENDING orders can be cancelled");
    }

    @Override
    public OrderState promotePendingToProcessing() {
        return this;
    }

    protected abstract Map<OrderStatus, Supplier<OrderState>> transitions();
}
