package com.example.orderprocessing.api.dto;

import com.example.orderprocessing.domain.model.OrderStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateOrderStatusRequest(
        @NotNull OrderStatus status,
        @NotNull @Min(0) Long version
) {
}
