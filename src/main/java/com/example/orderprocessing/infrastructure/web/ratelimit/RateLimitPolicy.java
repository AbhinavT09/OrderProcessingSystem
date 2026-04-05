package com.example.orderprocessing.infrastructure.web.ratelimit;

/**
 * Immutable runtime rate-limit policy resolved for an incoming request.
 *
 * <p>Combines baseline quota, burst tolerance, and fallback mode flags so
 * {@code RateLimitingFilter} can enforce consistent distributed limits.</p>
 *
 * @param capacity steady-state token bucket capacity for the policy window
 * @param windowMs policy time window in milliseconds
 * @param burstCapacity additional burst allowance above steady-state capacity
 * @param slidingWindowFallbackEnabled whether Redis sliding-window fallback is allowed
 * @param policyName human-readable policy identifier used for diagnostics
 */
public record RateLimitPolicy(
        int capacity,
        long windowMs,
        int burstCapacity,
        boolean slidingWindowFallbackEnabled,
        String policyName) {
}
