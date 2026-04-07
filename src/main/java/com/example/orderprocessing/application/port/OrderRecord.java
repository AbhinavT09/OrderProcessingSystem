package com.example.orderprocessing.application.port;

import com.example.orderprocessing.domain.order.OrderStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Persistence-neutral order snapshot used at application boundaries.
 *
 * @param id order id
 * @param version optimistic lock version
 * @param status order status
 * @param createdAt creation timestamp
 * @param idempotencyKey optional request idempotency key
 * @param ownerSubject JWT {@code sub} (or equivalent) of the principal who placed the order; may be null for legacy rows
 * @param regionId active region marker
 * @param lastUpdatedTimestamp conflict-resolution timestamp
 * @param items immutable item snapshot
 */
public record OrderRecord(
        UUID id,
        Long version,
        OrderStatus status,
        Instant createdAt,
        String idempotencyKey,
        String ownerSubject,
        String regionId,
        Instant lastUpdatedTimestamp,
        List<OrderItemRecord> items) {
}
