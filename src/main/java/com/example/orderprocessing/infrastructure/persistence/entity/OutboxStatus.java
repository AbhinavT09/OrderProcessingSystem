package com.example.orderprocessing.infrastructure.persistence.entity;

/**
 * Delivery lifecycle states for outbox rows.
 *
 * <p>{@code PENDING} indicates ready/new work, {@code FAILED} indicates retryable in-flight failure,
 * and {@code SENT} indicates successful publication.</p>
 */
public enum OutboxStatus {
    PENDING,
    SENT,
    FAILED
}
