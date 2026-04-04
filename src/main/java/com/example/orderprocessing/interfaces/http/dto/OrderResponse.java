package com.example.orderprocessing.interfaces.http.dto;

import com.example.orderprocessing.domain.order.OrderStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * OrderResponse record captures immutable data transferred between layers.
 * It is used to keep the boots the Spring runtime for the service layer explicit and maintainable in this architecture.
 */
public record OrderResponse(UUID id, OrderStatus status, Instant createdAt, List<OrderItemRequest> items) {
}
