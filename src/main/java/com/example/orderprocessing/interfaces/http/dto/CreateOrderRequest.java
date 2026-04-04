package com.example.orderprocessing.interfaces.http.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * HTTP command payload for order creation.
 *
 * <p>Interface-layer DTO validated at the API boundary before entering application services.</p>
 */
public record CreateOrderRequest(@NotEmpty @Valid List<OrderItemRequest> items) {
}
