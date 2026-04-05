package com.example.orderprocessing.application.exception;

/**
 * Domain/application conflict signal for rejected state transitions and concurrency races.
 *
 * <p><b>Architecture role:</b> application-layer exception translated by interface adapters into
 * HTTP {@code 409} responses.</p>
 *
 * <p><b>Distributed-systems context:</b> used for optimistic-concurrency mismatches, duplicate
 * in-progress idempotency keys, and conflict-policy rejections.</p>
 */
public class ConflictException extends RuntimeException {
    /**
     * Creates a conflict exception.
     * @param message domain conflict message
     */
    public ConflictException(String message) {
        super(message);
    }
}
