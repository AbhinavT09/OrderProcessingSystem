package com.example.orderprocessing.api.exception;

import com.example.orderprocessing.api.error.ApiError;
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
 * GlobalExceptionHandler implements a concrete responsibility in the order processing service.
 * It is used to keep the boots the Spring runtime for the service layer explicit and maintainable in this architecture.
 */
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    /**
     * Executes handleNotFound.
     * @param ex input argument used by this operation
     * @return operation result
     */
    public ResponseEntity<ApiError> handleNotFound(NotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    /**
     * Executes handleConflict.
     * @param ex input argument used by this operation
     * @return operation result
     */
    public ResponseEntity<ApiError> handleConflict(ConflictException ex) {
        return build(HttpStatus.CONFLICT, "CONFLICT", ex.getMessage());
    }

    @ExceptionHandler(InfrastructureException.class)
    /**
     * Executes handleInfrastructure.
     * @param ex input argument used by this operation
     * @return operation result
     */
    public ResponseEntity<ApiError> handleInfrastructure(InfrastructureException ex) {
        return build(HttpStatus.SERVICE_UNAVAILABLE, "INFRASTRUCTURE_ERROR", "Upstream dependency unavailable");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    /**
     * Executes handleValidation.
     * @param ex input argument used by this operation
     * @return operation result
     */
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + " " + err.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message.isBlank() ? "Validation failed" : message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    /**
     * Executes handleMalformedJson.
     * @param ex input argument used by this operation
     * @return operation result
     */
    public ResponseEntity<ApiError> handleMalformedJson(HttpMessageNotReadableException ex) {
        return build(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Malformed request body");
    }

    @ExceptionHandler(ConstraintViolationException.class)
    /**
     * Executes handleConstraintViolation.
     * @param ex input argument used by this operation
     * @return operation result
     */
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex) {
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    /**
     * Executes handleBadRequest.
     * @param ex input argument used by this operation
     * @return operation result
     */
    public ResponseEntity<ApiError> handleBadRequest(IllegalArgumentException ex) {
        return build(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    /**
     * Executes handleUnexpected.
     * @param ex input argument used by this operation
     * @return operation result
     */
    public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "UNEXPECTED_ERROR", "Unexpected server error");
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String code, String message) {
        ApiError body = new ApiError(code, message, MDC.get(RequestContextFilter.REQUEST_ID), Instant.now());
        return ResponseEntity.status(status).body(body);
    }
}
