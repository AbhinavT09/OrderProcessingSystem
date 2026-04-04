package com.example.orderprocessing.application.exception;

/**
 * ConflictException implements a concrete responsibility in the order processing service.
 * It is used to keep the boots the Spring runtime for the service layer explicit and maintainable in this architecture.
 */
public class ConflictException extends RuntimeException {
    /**
     * Creates a conflict exception.
     * @param message domain conflict message
     */
    public ConflictException(String message) {
        super(message);
    }
}
