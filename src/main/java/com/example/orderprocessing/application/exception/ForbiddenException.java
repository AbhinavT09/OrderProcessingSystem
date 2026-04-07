package com.example.orderprocessing.application.exception;

/**
 * Thrown when an authenticated caller is not allowed to perform the requested operation.
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
