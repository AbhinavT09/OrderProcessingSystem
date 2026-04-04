package com.example.orderprocessing.domain.model;

public enum OutboxStatus {
    PENDING,
    IN_PROGRESS,
    PUBLISHED,
    FAILED
}
