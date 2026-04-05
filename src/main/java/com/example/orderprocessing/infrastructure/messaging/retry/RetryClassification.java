package com.example.orderprocessing.infrastructure.messaging.retry;

public enum RetryClassification {
    TRANSIENT,
    SEMI_TRANSIENT,
    PERMANENT
}
