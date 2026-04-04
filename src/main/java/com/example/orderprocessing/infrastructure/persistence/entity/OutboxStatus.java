package com.example.orderprocessing.infrastructure.persistence.entity;

public enum OutboxStatus {
    PENDING,
    SENT,
    FAILED
}
