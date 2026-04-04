package com.example.orderprocessing.interfaces.http.dto;

import com.example.orderprocessing.domain.order.OrderStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * HTTP response model for order resources.
 *
 * <p>Exposes a client-facing projection of aggregate identity, status, timestamp, and items.</p>
 */
public record OrderResponse(UUID id, OrderStatus status, Instant createdAt, List<OrderItemRequest> items) {
}
