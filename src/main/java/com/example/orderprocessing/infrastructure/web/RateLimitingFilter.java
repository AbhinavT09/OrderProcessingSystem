package com.example.orderprocessing.infrastructure.web;

import com.example.orderprocessing.api.error.ApiError;
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
    private final ObjectMapper objectMapper;
    private final Counter allowedCounter;
    private final Counter blockedCounter;
    private final Counter redisConnectionFailures;
    private final Timer redisCommandLatency;
    private final int requestsPerWindow;
    private final long windowMs;
    private final double refillPerMs;

    public RateLimitingFilter(StringRedisTemplate redisTemplate,
                              ObjectMapper objectMapper,
                              MeterRegistry meterRegistry,
                              @Value("${app.security.rate-limit.requests:120}") int requestsPerWindow,
                              @Value("${app.security.rate-limit.window-ms:60000}") long windowMs) {
        this.redisTemplate = redisTemplate;
        this.tokenBucketScript = new DefaultRedisScript<>(TOKEN_BUCKET_LUA, Long.class);
        this.objectMapper = objectMapper;
        this.allowedCounter = meterRegistry.counter("rate_limit.allowed.count");
        this.blockedCounter = meterRegistry.counter("rate_limit.blocked.count");
        this.redisConnectionFailures = meterRegistry.counter("redis.connection.failures", "component", "rate_limiter");
        this.redisCommandLatency = meterRegistry.timer("redis.command.latency", "command", "evalsha", "component", "rate_limiter");
        this.requestsPerWindow = requestsPerWindow;
        this.windowMs = windowMs;
        this.refillPerMs = windowMs <= 0 ? 0D : ((double) requestsPerWindow) / (double) windowMs;
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
        long nowMs = System.currentTimeMillis();
        long ttlMs = Math.max(windowMs * KEY_TTL_MULTIPLIER, windowMs);
        try {
            Long allowed = redisCommandLatency.record(() -> redisTemplate.execute(
                    tokenBucketScript,
                    Collections.singletonList(key),
                    String.valueOf(nowMs),
                    String.valueOf(requestsPerWindow),
                    String.valueOf(refillPerMs),
                    String.valueOf(REQUEST_TOKENS),
                    String.valueOf(ttlMs)));
            return allowed != null && allowed == 1L;
        } catch (RuntimeException ex) {
            // Fail-open to protect API availability if Redis is degraded.
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
