package com.example.orderprocessing.infrastructure.messaging;

/**
 * DelayedProcessingNotReadyException implements a concrete responsibility in the order processing service.
 * It is used to keep the boots the Spring runtime for the service layer explicit and maintainable in this architecture.
 */
public class DelayedProcessingNotReadyException extends RuntimeException {
    /**
     * Creates an exception for events that are not due yet.
     * @param message scheduling context message
     */
    public DelayedProcessingNotReadyException(String message) {
        super(message);
    }
}
