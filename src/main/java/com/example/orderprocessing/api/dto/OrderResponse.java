package com.example.orderprocessing.api.dto;

import com.example.orderprocessing.domain.model.OrderStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(UUID id, OrderStatus status, Instant createdAt, List<OrderItemRequest> items) {
}
