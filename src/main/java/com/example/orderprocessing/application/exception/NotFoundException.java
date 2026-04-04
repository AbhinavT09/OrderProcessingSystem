package com.example.orderprocessing.application.exception;

/**
 * NotFoundException implements a concrete responsibility in the order processing service.
 * It is used to keep the boots the Spring runtime for the service layer explicit and maintainable in this architecture.
 */
public class NotFoundException extends RuntimeException {
    /**
     * Creates a not-found exception.
     * @param message missing-resource message
     */
    public NotFoundException(String message) {
        super(message);
    }
}
