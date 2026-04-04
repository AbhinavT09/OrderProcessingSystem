package com.example.orderprocessing.infrastructure.web;

import com.example.orderprocessing.api.error.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final class WindowCounter {
        long windowStartMs;
        int count;
    }

    private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final Counter rateLimitCounter;
    private final int requestsPerWindow;
    private final long windowMs;

    public RateLimitingFilter(ObjectMapper objectMapper,
                              MeterRegistry meterRegistry,
                              @Value("${app.security.rate-limit.requests:120}") int requestsPerWindow,
                              @Value("${app.security.rate-limit.window-ms:60000}") long windowMs) {
        this.objectMapper = objectMapper;
        this.rateLimitCounter = meterRegistry.counter("http.server.rate_limit.blocked.count");
        this.requestsPerWindow = requestsPerWindow;
        this.windowMs = windowMs;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator") || path.startsWith("/error");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String key = buildKey(request);
        long now = System.currentTimeMillis();

        WindowCounter state = counters.computeIfAbsent(key, k -> {
            WindowCounter wc = new WindowCounter();
            wc.windowStartMs = now;
            wc.count = 0;
            return wc;
        });

        synchronized (state) {
            if (now - state.windowStartMs >= windowMs) {
                state.windowStartMs = now;
                state.count = 0;
            }
            state.count++;
            if (state.count > requestsPerWindow) {
                rateLimitCounter.increment();
                writeRateLimitResponse(response);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private String buildKey(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        String subjectKey = "anonymous";
        if (authorization != null && authorization.startsWith("Bearer ")) {
            // Keep key cardinality bounded by hashing token material.
            subjectKey = Integer.toHexString(authorization.substring(7).hashCode());
        } else if (request.getUserPrincipal() != null) {
            subjectKey = request.getUserPrincipal().getName();
        }
        String ip = request.getRemoteAddr();
        return request.getMethod() + ":" + request.getRequestURI() + ":" + subjectKey + ":" + ip;
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
