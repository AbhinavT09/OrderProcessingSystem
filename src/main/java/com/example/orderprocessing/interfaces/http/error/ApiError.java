package com.example.orderprocessing.interfaces.http.error;

import java.time.Instant;

/**
 * ApiError record captures immutable data transferred between layers.
 * It is used to keep the boots the Spring runtime for the service layer explicit and maintainable in this architecture.
 */
public record ApiError(
        String code,
        String message,
        String requestId,
        Instant timestamp
) {
}
