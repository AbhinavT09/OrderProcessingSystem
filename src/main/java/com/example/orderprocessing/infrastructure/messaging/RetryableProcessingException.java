package com.example.orderprocessing.infrastructure.messaging;

/**
 * Transient processing failure signal for retry-topic orchestration.
 *
 * <p><b>Architecture role:</b> infrastructure consumer exception used to mark failures as
 * retryable without masking root cause.</p>
 *
 * <p><b>Resilience context:</b> thrown for recoverable DB or timing faults so retries are handled
 * by Kafka retry topics instead of blocking listener threads.</p>
 */
public class RetryableProcessingException extends RuntimeException {
    /**
     * Creates a retryable processing exception.
     * @param message retry context message
     * @param cause underlying transient failure
     */
    public RetryableProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
