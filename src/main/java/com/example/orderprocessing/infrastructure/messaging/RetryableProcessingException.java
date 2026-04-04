package com.example.orderprocessing.infrastructure.messaging;

public class RetryableProcessingException extends RuntimeException {
    public RetryableProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
