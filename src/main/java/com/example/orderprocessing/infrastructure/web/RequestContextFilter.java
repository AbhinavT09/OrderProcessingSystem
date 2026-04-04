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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component("requestIdFilter")
/**
 * Infrastructure web filter that injects request correlation and region context.
 *
 * <p>Ensures every request has stable identifiers for tracing/logging and records
 * request/latency/error metrics tagged by method, path, status, and region.</p>
 */
public class RequestContextFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID = "request_id";
    public static final String REGION_ID = "region_id";
    private static final String HEADER = "X-Request-Id";
    private static final String REGION_HEADER = "X-Region-Id";
    private final MeterRegistry meterRegistry;
    private final Timer requestLatencyTimer;
    private final String defaultRegionId;

    /**
     * Creates request-context and region-tagging filter.
     * @param meterRegistry metrics registry
     * @param regionId configured region identifier
     */
    public RequestContextFilter(MeterRegistry meterRegistry,
                                @Value("${app.multi-region.region-id:region-a}") String defaultRegionId) {
        this.meterRegistry = meterRegistry;
        this.defaultRegionId = defaultRegionId;
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
        String regionId = request.getHeader(REGION_HEADER);
        if (regionId == null || regionId.isBlank()) {
            regionId = defaultRegionId;
        }

        MDC.put(REQUEST_ID, requestId);
        MDC.put("requestId", requestId);
        MDC.put(REGION_ID, regionId);
        response.setHeader(HEADER, requestId);
        response.setHeader(REGION_HEADER, regionId);
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
                    .tag("region", regionId)
                    .register(meterRegistry)
                    .increment();
            Counter.builder("http.server.requests.by.region")
                    .tag("region", regionId)
                    .tag("method", request.getMethod())
                    .register(meterRegistry)
                    .increment();
            if (response.getStatus() >= 500) {
                Counter.builder("http.server.request.errors")
                        .tag("method", request.getMethod())
                        .tag("path", request.getRequestURI())
                        .tag("region", regionId)
                        .register(meterRegistry)
                        .increment();
            }
            MDC.remove(REQUEST_ID);
            MDC.remove("requestId");
            MDC.remove(REGION_ID);
        }
    }
}
