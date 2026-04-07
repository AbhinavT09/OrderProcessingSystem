package com.example.orderprocessing.interfaces.http.exception;

import com.example.orderprocessing.application.exception.ConflictException;
import com.example.orderprocessing.application.exception.ForbiddenException;
import com.example.orderprocessing.application.exception.InfrastructureException;
import com.example.orderprocessing.application.exception.NotFoundException;
import com.example.orderprocessing.interfaces.http.error.ApiError;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.validation.ConstraintViolationException;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(new SimpleMeterRegistry());

    @Test
    void handleNotFound_mapsTo404() {
        ResponseEntity<ApiError> r = handler.handleNotFound(new NotFoundException("missing"));
        assertEquals(HttpStatus.NOT_FOUND, r.getStatusCode());
        assertEquals("NOT_FOUND", r.getBody().code());
    }

    @Test
    void handleConflict_mapsTo409() {
        ResponseEntity<ApiError> r = handler.handleConflict(new ConflictException("race"));
        assertEquals(HttpStatus.CONFLICT, r.getStatusCode());
        assertEquals("CONFLICT", r.getBody().code());
    }

    @Test
    void handleForbidden_mapsTo403() {
        ResponseEntity<ApiError> r = handler.handleForbidden(new ForbiddenException("nope"));
        assertEquals(HttpStatus.FORBIDDEN, r.getStatusCode());
    }

    @Test
    void handleInfrastructure_mapsTo503WithSafeMessage() {
        ResponseEntity<ApiError> r = handler.handleInfrastructure(
                new InfrastructureException("internal detail", null));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, r.getStatusCode());
        assertEquals("INFRASTRUCTURE_ERROR", r.getBody().code());
        assertEquals("Upstream dependency unavailable", r.getBody().message());
    }

    @Test
    void handleValidation_joinsFieldErrors() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult br = mock(BindingResult.class);
        when(ex.getBindingResult()).thenReturn(br);
        when(br.getFieldErrors()).thenReturn(List.of(
                new FieldError("req", "items", "must not be empty"),
                new FieldError("req", "name", "must not be blank")));
        ResponseEntity<ApiError> r = handler.handleValidation(ex);
        assertEquals(HttpStatus.BAD_REQUEST, r.getStatusCode());
        assertEquals("VALIDATION_FAILED", r.getBody().code());
        assertTrue(r.getBody().message().contains("items"));
    }

    @Test
    void handleMalformedJson_mapsTo400() {
        ResponseEntity<ApiError> r = handler.handleMalformedJson(
                new HttpMessageNotReadableException("bad json", null, null));
        assertEquals(HttpStatus.BAD_REQUEST, r.getStatusCode());
        assertEquals("MALFORMED_REQUEST", r.getBody().code());
    }

    @Test
    void handleConstraintViolation_mapsTo400() {
        ResponseEntity<ApiError> r = handler.handleConstraintViolation(
                new ConstraintViolationException("path invalid", Collections.emptySet()));
        assertEquals(HttpStatus.BAD_REQUEST, r.getStatusCode());
    }

    @Test
    void handleBadRequest_mapsIllegalArgument() {
        ResponseEntity<ApiError> r = handler.handleBadRequest(new IllegalArgumentException("bad arg"));
        assertEquals(HttpStatus.BAD_REQUEST, r.getStatusCode());
        assertEquals("BAD_REQUEST", r.getBody().code());
    }

    @Test
    void handleUnexpected_mapsTo500AndIncrementsCounter() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        GlobalExceptionHandler h = new GlobalExceptionHandler(registry);
        ResponseEntity<ApiError> r = h.handleUnexpected(new RuntimeException("surprise"));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, r.getStatusCode());
        assertEquals(1.0, registry.get("api.errors.unexpected").counter().count());
    }
}
