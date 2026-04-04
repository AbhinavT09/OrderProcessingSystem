package com.example.orderprocessing.infrastructure.messaging.schema;

public class EventSchemaValidationException extends RuntimeException {
    public EventSchemaValidationException(String message) {
        super(message);
    }

    public EventSchemaValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
