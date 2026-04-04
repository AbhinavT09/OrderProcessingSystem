package com.example.orderprocessing.interfaces.http.exception;

import com.example.orderprocessing.interfaces.http.error.ApiError;
import com.example.orderprocessing.application.exception.ConflictException;
import com.example.orderprocessing.application.exception.InfrastructureException;
import com.example.orderprocessing.application.exception.NotFoundException;
import com.example.orderprocessing.infrastructure.web.RequestContextFilter;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.stream.Collectors;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
/**
 * Interface-layer exception translator for HTTP responses.
 *
 * <p>Maps application/infrastructure failures to stable API error envelopes and status codes,
 * preserving request correlation id for traceability.</p>
 */
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    /**
     * Maps missing-resource failures to {@code 404 NOT_FOUND}.
     *
     * @param ex application exception with domain-specific details
     * @return standardized API error response
     */
    public ResponseEntity<ApiError> handleNotFound(NotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    /**
     * Maps business conflicts to {@code 409 CONFLICT}.
     *
     * @param ex conflict details
     * @return standardized API error response
     */
    public ResponseEntity<ApiError> handleConflict(ConflictException ex) {
        return build(HttpStatus.CONFLICT, "CONFLICT", ex.getMessage());
    }

    @ExceptionHandler(InfrastructureException.class)
    /**
     * Maps dependency failures to {@code 503 SERVICE_UNAVAILABLE}.
     *
     * @param ex infrastructure-level exception
     * @return standardized API error response with safe message
     */
    public ResponseEntity<ApiError> handleInfrastructure(InfrastructureException ex) {
        return build(HttpStatus.SERVICE_UNAVAILABLE, "INFRASTRUCTURE_ERROR", "Upstream dependency unavailable");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    /**
     * Maps bean-validation failures on request bodies to {@code 400 BAD_REQUEST}.
     *
     * @param ex validation exception with field-level details
     * @return standardized API error response
     */
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + " " + err.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message.isBlank() ? "Validation failed" : message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    /**
     * Maps malformed JSON payloads to {@code 400 BAD_REQUEST}.
     *
     * @param ex request deserialization failure
     * @return standardized API error response
     */
    public ResponseEntity<ApiError> handleMalformedJson(HttpMessageNotReadableException ex) {
        return build(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Malformed request body");
    }

    @ExceptionHandler(ConstraintViolationException.class)
    /**
     * Maps parameter/path constraint violations to {@code 400 BAD_REQUEST}.
     *
     * @param ex constraint violation details
     * @return standardized API error response
     */
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex) {
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    /**
     * Maps semantic request errors to {@code 400 BAD_REQUEST}.
     *
     * @param ex argument-related failure
     * @return standardized API error response
     */
    public ResponseEntity<ApiError> handleBadRequest(IllegalArgumentException ex) {
        return build(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    /**
     * Provides safe fallback mapping for unexpected failures.
     *
     * @param ex uncaught exception
     * @return standardized API error response with generic message
     */
    public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "UNEXPECTED_ERROR", "Unexpected server error");
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String code, String message) {
        ApiError body = new ApiError(code, message, MDC.get(RequestContextFilter.REQUEST_ID), Instant.now());
        return ResponseEntity.status(status).body(body);
    }
}
