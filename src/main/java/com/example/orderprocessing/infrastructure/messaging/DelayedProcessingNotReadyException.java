package com.example.orderprocessing.infrastructure.messaging;

public class DelayedProcessingNotReadyException extends RuntimeException {
    public DelayedProcessingNotReadyException(String message) {
        super(message);
    }
}
