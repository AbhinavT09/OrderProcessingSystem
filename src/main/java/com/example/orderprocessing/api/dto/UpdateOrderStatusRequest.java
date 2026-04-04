package com.example.orderprocessing.api.dto;

import com.example.orderprocessing.domain.model.OrderStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateOrderStatusRequest(@NotNull OrderStatus status) {
}
