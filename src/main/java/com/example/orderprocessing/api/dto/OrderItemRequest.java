package com.example.orderprocessing.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record OrderItemRequest(
        @NotBlank String productName,
        @NotNull @Min(1) Integer quantity,
        @NotNull @Min(0) Double price
) {
}
