package com.example.orderprocessing.application.exception;

/**
 * Domain/application not-found signal for missing resources.
 *
 * <p><b>Architecture role:</b> application-layer exception translated by interface adapters into
 * HTTP {@code 404} responses.</p>
 *
 * <p><b>Transaction boundary:</b> causes transaction rollback when thrown inside transactional
 * command handlers.</p>
 */
public class NotFoundException extends RuntimeException {
    /**
     * Creates a not-found exception.
     * @param message missing-resource message
     */
    public NotFoundException(String message) {
        super(message);
    }
}
