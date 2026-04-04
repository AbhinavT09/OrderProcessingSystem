package com.example.orderprocessing.interfaces.http.error;

import java.time.Instant;

/**
 * Standardized HTTP error envelope returned by the interface layer.
 *
 * <p>Provides stable error code/message semantics plus request correlation metadata.</p>
 */
public record ApiError(
        String code,
        String message,
        String requestId,
        Instant timestamp
) {
}
