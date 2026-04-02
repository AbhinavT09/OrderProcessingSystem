package com.example.orderprocessing.model;

import jakarta.validation.constraints.NotNull;

public class UpdateOrderStatusRequest {

    @NotNull
    private OrderStatus status;

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }
}
