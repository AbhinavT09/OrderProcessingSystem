package com.example.orderprocessing.infrastructure.messaging;

/**
 * RetryableProcessingException implements a concrete responsibility in the order processing service.
 * It is used to keep the boots the Spring runtime for the service layer explicit and maintainable in this architecture.
 */
public class RetryableProcessingException extends RuntimeException {
    /**
     * Creates a retryable processing exception.
     * @param message retry context message
     * @param cause underlying transient failure
     */
    public RetryableProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
