package com.example.orderprocessing.infrastructure.web.ratelimit;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Strategy port for resolving request-scoped rate-limit policies.
 *
 * <p>Implementations may derive policies from static config, Redis, user profile,
 * endpoint criticality, and live system pressure signals.</p>
 */
public interface RateLimitPolicyProvider {

    /**
     * Resolves the active policy for an HTTP request.
     *
     * @param request incoming HTTP request
     * @return resolved policy used by distributed rate-limiting logic
     */
    RateLimitPolicy resolve(HttpServletRequest request);
}
