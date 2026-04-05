package com.example.orderprocessing.application.exception;

/**
 * Infrastructure-failure signal surfaced to the interface layer.
 *
 * <p><b>Architecture role:</b> application-layer exception used when downstream dependencies are
 * degraded (for example passive-region write gating or critical backpressure).</p>
 *
 * <p><b>Transaction boundary:</b> triggers rollback in transactional command flows and maps to
 * HTTP {@code 503} through exception handlers.</p>
 */
public class InfrastructureException extends RuntimeException {
    /**
     * Creates an infrastructure exception with cause.
     * @param message infrastructure failure description
     * @param cause underlying cause
     */
    public InfrastructureException(String message, Throwable cause) {
        super(message, cause);
    }
}
