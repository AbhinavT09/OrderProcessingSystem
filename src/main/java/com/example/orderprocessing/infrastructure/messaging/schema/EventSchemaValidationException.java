package com.example.orderprocessing.infrastructure.messaging.schema;

/**
 * EventSchemaValidationException implements a concrete responsibility in the order processing service.
 * It is used to keep the boots the Spring runtime for the service layer explicit and maintainable in this architecture.
 */
public class EventSchemaValidationException extends RuntimeException {
    /**
     * Creates a schema validation exception.
     * @param message validation failure details
     */
    public EventSchemaValidationException(String message) {
        super(message);
    }

    /**
     * Creates a schema validation exception with cause.
     * @param message validation failure details
     * @param cause underlying parsing/serialization cause
     */
    public EventSchemaValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
