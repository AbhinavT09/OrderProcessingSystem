package com.example.orderprocessing.application.exception;

/**
 * InfrastructureException implements a concrete responsibility in the order processing service.
 * It is used to keep the boots the Spring runtime for the service layer explicit and maintainable in this architecture.
 */
public class InfrastructureException extends RuntimeException {
    /**
     * Creates an infrastructure exception with cause.
     * @param message infrastructure failure description
     * @param cause underlying cause
     */
    public InfrastructureException(String message, Throwable cause) {
        super(message, cause);
    }
}
