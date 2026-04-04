package com.example.orderprocessing.domain.order;

/**
 * OrderStatus enum enumerates constrained values used by the workflow.
 * It is used to keep the boots the Spring runtime for the service layer explicit and maintainable in this architecture.
 */
public enum OrderStatus {
    PENDING,
    PROCESSING,
    SHIPPED,
    DELIVERED,
    CANCELLED
}
