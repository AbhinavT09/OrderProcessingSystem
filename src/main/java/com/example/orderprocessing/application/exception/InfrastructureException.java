package com.example.orderprocessing.application.exception;

public class InfrastructureException extends RuntimeException {
    public InfrastructureException(String message, Throwable cause) {
        super(message, cause);
    }
}
