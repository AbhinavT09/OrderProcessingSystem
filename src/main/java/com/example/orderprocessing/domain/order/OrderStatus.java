package com.example.orderprocessing.domain.order;

/**
 * Canonical domain lifecycle states for an order aggregate.
 *
 * <p>Used by the state pattern and external representations to reason about allowed
 * transitions and business progression.</p>
 */
public enum OrderStatus {
    PENDING,
    PROCESSING,
    SHIPPED,
    DELIVERED,
    CANCELLED
}
