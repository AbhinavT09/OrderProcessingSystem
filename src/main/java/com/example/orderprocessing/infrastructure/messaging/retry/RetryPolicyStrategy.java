package com.example.orderprocessing.infrastructure.messaging.retry;

public interface RetryPolicyStrategy {

    RetryPlan plan(Throwable throwable, int retryCount);

    record RetryPlan(RetryClassification classification, long delayMs, String failureType, String failureReason) {
    }
}
