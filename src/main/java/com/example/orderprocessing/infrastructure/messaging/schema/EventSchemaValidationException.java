package com.example.orderprocessing.infrastructure.messaging.schema;

/**
 * Exception raised when event schema contracts are violated.
 *
 * <p><b>Architecture role:</b> infrastructure messaging exception used by schema adapters to
 * fail malformed payloads before business processing.</p>
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
