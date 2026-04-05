package com.example.orderprocessing.infrastructure.web;

import com.example.orderprocessing.interfaces.http.error.ApiError;
import com.example.orderprocessing.infrastructure.resilience.BackpressureManager;
import com.example.orderprocessing.infrastructure.web.ratelimit.RateLimitPolicy;
import com.example.orderprocessing.infrastructure.web.ratelimit.RateLimitPolicyProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import org.slf4j.MDC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
/**
 * Infrastructure web filter enforcing per-client request rate limits.
 *
 * <p>Implements dynamic, Redis-backed distributed throttling with:</p>
 * <ul>
 *   <li>request-scoped policy lookup (tier + endpoint + load-adaptive adjustments),</li>
 *   <li>token-bucket primary enforcement via Lua for atomic consume/refill,</li>
 *   <li>optional sliding-window fallback when token-bucket execution fails.</li>
 * </ul>
 *
 * <p>Filter preserves API availability by failing open when Redis is unavailable.</p>
 */
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitingFilter.class);

    private static final String TOKEN_BUCKET_LUA = """
            local key = KEYS[1]
            local now = tonumber(ARGV[1])
            local capacity = tonumber(ARGV[2])
            local refill_per_ms = tonumber(ARGV[3])
            local requested = tonumber(ARGV[4])
            local ttl_ms = tonumber(ARGV[5])

            local data = redis.call('HMGET', key, 'tokens', 'ts')
            local tokens = tonumber(data[1])
            local ts = tonumber(data[2])
            if not tokens then
                tokens = capacity
                ts = now
            end

            local delta = math.max(0, now - ts)
            tokens = math.min(capacity, tokens + (delta * refill_per_ms))

            local allowed = 0
            if tokens >= requested then
                tokens = tokens - requested
                allowed = 1
            end

            redis.call('HMSET', key, 'tokens', tokens, 'ts', now)
            redis.call('PEXPIRE', key, ttl_ms)
            return allowed
            """;

    private static final long REQUEST_TOKENS = 1L;
    private static final long KEY_TTL_MULTIPLIER = 2L;

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<Long> tokenBucketScript;
    private final RedisScript<Long> slidingWindowScript;
    private final ObjectMapper objectMapper;
    private final RateLimitPolicyProvider policyProvider;
    private final BackpressureManager backpressureManager;
    private final Counter allowedCounter;
    private final Counter blockedCounter;
    private final Counter redisConnectionFailures;
    private final Counter dynamicAdjustmentCounter;
    private final Counter rejectionByPolicyCounter;
    private final Timer redisCommandLatency;

    /**
     * Creates the dynamic distributed rate-limiting filter.
     * @param redisTemplate redis template for Lua script execution
     * @param objectMapper object mapper for error payloads
     * @param policyProvider dynamic policy resolver
     * @param backpressureManager global backpressure signal provider
     * @param meterRegistry metrics registry
     * @param requestsPerWindow default steady-state request capacity
     * @param windowMs default policy window in milliseconds
     */
    public RateLimitingFilter(StringRedisTemplate redisTemplate,
                              ObjectMapper objectMapper,
                              RateLimitPolicyProvider policyProvider,
                              BackpressureManager backpressureManager,
                              MeterRegistry meterRegistry,
                              @Value("${app.security.rate-limit.requests:120}") int requestsPerWindow,
                              @Value("${app.security.rate-limit.window-ms:60000}") long windowMs) {
        this.redisTemplate = redisTemplate;
        this.tokenBucketScript = new DefaultRedisScript<>(TOKEN_BUCKET_LUA, Long.class);
        this.slidingWindowScript = new DefaultRedisScript<>("""
                local key = KEYS[1]
                local now = tonumber(ARGV[1])
                local window_ms = tonumber(ARGV[2])
                local limit = tonumber(ARGV[3])
                local ttl_ms = tonumber(ARGV[4])
                redis.call('ZREMRANGEBYSCORE', key, '-inf', now - window_ms)
                local count = redis.call('ZCARD', key)
                if count < limit then
                    redis.call('ZADD', key, now, tostring(now))
                    redis.call('PEXPIRE', key, ttl_ms)
                    return 1
                end
                redis.call('PEXPIRE', key, ttl_ms)
                return 0
                """, Long.class);
        this.objectMapper = objectMapper;
        this.policyProvider = policyProvider;
        this.backpressureManager = backpressureManager;
        this.allowedCounter = meterRegistry.counter("rate_limit.allowed.count");
        this.blockedCounter = meterRegistry.counter("rate_limit.blocked.count");
        this.redisConnectionFailures = meterRegistry.counter("redis.connection.failures", "component", "rate_limiter");
        this.dynamicAdjustmentCounter = meterRegistry.counter("rate_limit.dynamic.adjustments");
        this.rejectionByPolicyCounter = meterRegistry.counter("rate_limit.rejections.by.policy");
        this.redisCommandLatency = meterRegistry.timer("redis.command.latency", "command", "evalsha", "component", "rate_limiter");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator") || path.startsWith("/error");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!isRequestAllowed(request)) {
            blockedCounter.increment();
            writeRateLimitResponse(response);
            return;
        }
        allowedCounter.increment();
        filterChain.doFilter(request, response);
    }

    private String buildKey(HttpServletRequest request) {
        String userId = request.getUserPrincipal() != null ? request.getUserPrincipal().getName() : "anonymous";
        String path = request.getRequestURI();
        String ip = request.getRemoteAddr();
        return "rate_limit:" + userId + ":" + path + ":" + ip;
    }

    private boolean isRequestAllowed(HttpServletRequest request) {
        String key = buildKey(request);
        RateLimitPolicy policy = policyProvider.resolve(request);
        if (backpressureManager.level() != BackpressureManager.Level.NORMAL) {
            dynamicAdjustmentCounter.increment();
        }
        long nowMs = System.currentTimeMillis();
        long ttlMs = Math.max(policy.windowMs() * KEY_TTL_MULTIPLIER, policy.windowMs());
        double refillPerMs = policy.windowMs() <= 0 ? 0D : ((double) policy.capacity()) / (double) policy.windowMs();
        try {
            Long allowed = redisCommandLatency.record(() -> redisTemplate.execute(
                    tokenBucketScript,
                    Collections.singletonList(key),
                    String.valueOf(nowMs),
                    String.valueOf(policy.capacity() + policy.burstCapacity()),
                    String.valueOf(refillPerMs),
                    String.valueOf(REQUEST_TOKENS),
                    String.valueOf(ttlMs)));
            if (allowed != null && allowed == 1L) {
                return true;
            }
            rejectionByPolicyCounter.increment();
            return false;
        } catch (RuntimeException ex) {
            if (policy.slidingWindowFallbackEnabled()) {
                try {
                    Long fallbackAllowed = redisCommandLatency.record(() -> redisTemplate.execute(
                            slidingWindowScript,
                            List.of(key + ":sw"),
                            String.valueOf(nowMs),
                            String.valueOf(policy.windowMs()),
                            String.valueOf(Math.max(1, policy.capacity())),
                            String.valueOf(ttlMs)));
                    if (fallbackAllowed != null && fallbackAllowed == 1L) {
                        return true;
                    }
                    rejectionByPolicyCounter.increment();
                    return false;
                } catch (RuntimeException fallbackEx) {
                    redisConnectionFailures.increment();
                    log.warn("Redis rate limiting fallback failure key={} reason={}", key, fallbackEx.toString());
                    return true;
                }
            }
            redisConnectionFailures.increment();
            log.warn("Redis rate limiting failure key={} reason={}", key, ex.toString());
            return true;
        }
    }

    private void writeRateLimitResponse(HttpServletResponse response) throws IOException {
        response.setStatus(429);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiError body = new ApiError(
                "RATE_LIMITED",
                "Too many requests",
                MDC.get(RequestContextFilter.REQUEST_ID),
                Instant.now());
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
