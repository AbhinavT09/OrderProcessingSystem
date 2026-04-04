package com.example.orderprocessing.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * OrderItemRequest record captures immutable data transferred between layers.
 * It is used to keep the boots the Spring runtime for the service layer explicit and maintainable in this architecture.
 */
public record OrderItemRequest(
        @NotBlank String productName,
        @NotNull @Min(1) Integer quantity,
        @NotNull @Min(0) Double price
) {
}
