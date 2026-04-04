package com.example.orderprocessing.infrastructure.persistence.entity;

/**
 * OutboxStatus enum enumerates constrained values used by the workflow.
 * It is used to keep the boots the Spring runtime for the service layer explicit and maintainable in this architecture.
 */
public enum OutboxStatus {
    PENDING,
    SENT,
    FAILED
}
