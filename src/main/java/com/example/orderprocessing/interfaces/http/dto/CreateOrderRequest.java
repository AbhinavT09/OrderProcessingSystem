package com.example.orderprocessing.interfaces.http.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * CreateOrderRequest record captures immutable data transferred between layers.
 * It is used to keep the boots the Spring runtime for the service layer explicit and maintainable in this architecture.
 */
public record CreateOrderRequest(@NotEmpty @Valid List<OrderItemRequest> items) {
}
