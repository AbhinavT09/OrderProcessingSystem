package com.example.orderprocessing.infrastructure.web.ratelimit;

import com.example.orderprocessing.infrastructure.resilience.BackpressureManager;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
/**
 * Redis-backed provider for dynamic per-request rate-limit policies.
 *
 * <p>Resolution flow:</p>
 * <ul>
 *   <li>Build policy key from user tier and endpoint class.</li>
 *   <li>Use short-lived local cache to avoid Redis hot-key pressure.</li>
 *   <li>Load policy from Redis when cache miss occurs.</li>
 *   <li>Apply adaptive tightening based on {@code BackpressureManager} level.</li>
 * </ul>
 *
 * <p>On Redis/config parsing failures, provider falls back to safe defaults
 * to preserve API availability.</p>
 */
public class RedisBackedRateLimitPolicyProvider implements RateLimitPolicyProvider {

    private record CachedPolicy(RateLimitPolicy policy, long expiresAtEpochMs) {
    }

    private final StringRedisTemplate redisTemplate;
    private final BackpressureManager backpressureManager;
    private final int defaultRequests;
    private final long defaultWindowMs;
    private final Duration cacheTtl;
    private final ConcurrentHashMap<String, CachedPolicy> localCache = new ConcurrentHashMap<>();

    public RedisBackedRateLimitPolicyProvider(
            StringRedisTemplate redisTemplate,
            BackpressureManager backpressureManager,
            @Value("${app.security.rate-limit.requests:120}") int defaultRequests,
            @Value("${app.security.rate-limit.window-ms:60000}") long defaultWindowMs,
            @Value("${app.security.rate-limit.policy-cache-ttl-ms:10000}") long cacheTtlMs) {
        this.redisTemplate = redisTemplate;
        this.backpressureManager = backpressureManager;
        this.defaultRequests = Math.max(10, defaultRequests);
        this.defaultWindowMs = Math.max(1000, defaultWindowMs);
        this.cacheTtl = Duration.ofMillis(Math.max(1000, cacheTtlMs));
    }

    @Override
    /**
     * Resolves policy for the current request and applies load-based adaptation.
     *
     * @param request incoming HTTP request
     * @return adapted policy used by rate-limiting filter
     */
    public RateLimitPolicy resolve(HttpServletRequest request) {
        String tier = resolveTier(request);
        String endpointClass = endpointClass(request.getMethod(), request.getRequestURI());
        String key = "rate_limit:policy:" + tier + ":" + endpointClass;
        long now = System.currentTimeMillis();
        CachedPolicy cached = localCache.get(key);
        if (cached != null && cached.expiresAtEpochMs() >= now) {
            return applyAdaptiveAdjustment(cached.policy());
        }

        RateLimitPolicy fromRedis = loadFromRedis(tier, endpointClass);
        localCache.put(key, new CachedPolicy(fromRedis, now + cacheTtl.toMillis()));
        return applyAdaptiveAdjustment(fromRedis);
    }

    private RateLimitPolicy loadFromRedis(String tier, String endpointClass) {
        try {
            String base = "rate_limit:config:" + tier + ":" + endpointClass + ":";
            int capacity = parseInt(redisTemplate.opsForValue().get(base + "capacity"), defaultRequests);
            long windowMs = parseLong(redisTemplate.opsForValue().get(base + "windowMs"), defaultWindowMs);
            int burst = parseInt(redisTemplate.opsForValue().get(base + "burst"), Math.max(1, capacity / 4));
            boolean sliding = parseBoolean(redisTemplate.opsForValue().get(base + "slidingWindowFallback"), true);
            return new RateLimitPolicy(
                    Math.max(1, capacity),
                    Math.max(500, windowMs),
                    Math.max(1, burst),
                    sliding,
                    tier + ":" + endpointClass);
        } catch (RuntimeException ignored) {
            return new RateLimitPolicy(defaultRequests, defaultWindowMs, Math.max(1, defaultRequests / 4), true, "default");
        }
    }

    private RateLimitPolicy applyAdaptiveAdjustment(RateLimitPolicy policy) {
        double factor = backpressureManager.throttlingFactor();
        int adjustedCapacity = Math.max(1, (int) Math.floor(policy.capacity() * factor));
        int adjustedBurst = Math.max(1, (int) Math.floor(policy.burstCapacity() * factor));
        return new RateLimitPolicy(
                adjustedCapacity,
                policy.windowMs(),
                adjustedBurst,
                policy.slidingWindowFallbackEnabled(),
                policy.policyName());
    }

    private String resolveTier(HttpServletRequest request) {
        String fromHeader = request.getHeader("X-User-Tier");
        if (fromHeader != null && !fromHeader.isBlank()) {
            return fromHeader.trim().toLowerCase(Locale.ROOT);
        }
        String principal = request.getUserPrincipal() != null ? request.getUserPrincipal().getName() : "anonymous";
        if (principal.startsWith("premium-")) {
            return "premium";
        }
        return "free";
    }

    private String endpointClass(String method, String requestUri) {
        String uri = Objects.requireNonNullElse(requestUri, "/");
        if ((uri.startsWith("/api/orders") || uri.startsWith("/orders")) && !"GET".equalsIgnoreCase(method)) {
            return "critical-write";
        }
        if (uri.startsWith("/api/orders") || uri.startsWith("/orders")) {
            return "standard";
        }
        return "default";
    }

    private int parseInt(String value, int defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private long parseLong(String value, long defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private boolean parseBoolean(String value, boolean defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return "true".equalsIgnoreCase(value) || "1".equals(value);
    }
}
