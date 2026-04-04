package com.example.orderprocessing.interfaces.http.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * HTTP DTO representing one order line item in create requests.
 *
 * <p>Defines interface-layer validation constraints for product, quantity, and price fields.</p>
 */
public record OrderItemRequest(
        @NotBlank String productName,
        @NotNull @Min(1) Integer quantity,
        @NotNull @Min(0) Double price
) {
}
