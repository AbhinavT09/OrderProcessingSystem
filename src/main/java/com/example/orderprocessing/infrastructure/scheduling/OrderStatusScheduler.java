package com.example.orderprocessing.infrastructure.scheduling;

import com.example.orderprocessing.application.service.OrderApplicationService;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OrderStatusScheduler {

    private static final Logger log = LoggerFactory.getLogger(OrderStatusScheduler.class);

    private final OrderApplicationService service;
    private final MeterRegistry meterRegistry;

    public OrderStatusScheduler(OrderApplicationService service, MeterRegistry meterRegistry) {
        this.service = service;
        this.meterRegistry = meterRegistry;
    }

    @Scheduled(fixedRateString = "${app.order-pending-promote-ms:300000}")
    public void promotePendingOrders() {
        try {
            int promoted = service.promotePendingToProcessing();
            meterRegistry.counter("orders.promoted.processing").increment(promoted);
        } catch (Exception ex) {
            meterRegistry.counter("orders.promoted.processing.failures").increment();
            log.error("Failed to promote pending orders", ex);
        }
    }
}
