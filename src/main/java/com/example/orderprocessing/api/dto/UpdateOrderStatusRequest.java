package com.example.orderprocessing.api.dto;

import com.example.orderprocessing.domain.model.OrderStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * UpdateOrderStatusRequest record captures immutable data transferred between layers.
 * It is used to keep the boots the Spring runtime for the service layer explicit and maintainable in this architecture.
 */
public record UpdateOrderStatusRequest(
        @NotNull OrderStatus status,
        @NotNull @Min(0) Long version
) {
}
