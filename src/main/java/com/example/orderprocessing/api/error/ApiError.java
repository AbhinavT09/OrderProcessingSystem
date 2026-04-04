package com.example.orderprocessing.api.error;

import java.time.Instant;

public record ApiError(
        String code,
        String message,
        String requestId,
        Instant timestamp
) {
}
