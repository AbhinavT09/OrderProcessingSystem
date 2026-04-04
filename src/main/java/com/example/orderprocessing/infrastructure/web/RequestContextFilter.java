package com.example.orderprocessing.infrastructure.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component("requestIdFilter")
public class RequestContextFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID = "request_id";
    private static final String HEADER = "X-Request-Id";
    private final MeterRegistry meterRegistry;
    private final Timer requestLatencyTimer;

    public RequestContextFilter(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.requestLatencyTimer = Timer.builder("http.server.request.latency")
                .publishPercentiles(0.95, 0.99)
                .register(meterRegistry);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestId = request.getHeader(HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }

        MDC.put(REQUEST_ID, requestId);
        MDC.put("requestId", requestId);
        response.setHeader(HEADER, requestId);
        long startNs = System.nanoTime();

        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationNs = System.nanoTime() - startNs;
            requestLatencyTimer.record(durationNs, java.util.concurrent.TimeUnit.NANOSECONDS);
            Counter.builder("http.server.request.count")
                    .tag("method", request.getMethod())
                    .tag("path", request.getRequestURI())
                    .tag("status", String.valueOf(response.getStatus()))
                    .register(meterRegistry)
                    .increment();
            if (response.getStatus() >= 500) {
                Counter.builder("http.server.request.errors")
                        .tag("method", request.getMethod())
                        .tag("path", request.getRequestURI())
                        .register(meterRegistry)
                        .increment();
            }
            MDC.remove(REQUEST_ID);
            MDC.remove("requestId");
        }
    }
}
