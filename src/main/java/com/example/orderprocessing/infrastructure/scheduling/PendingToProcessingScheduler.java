package com.example.orderprocessing.infrastructure.scheduling;

import com.example.orderprocessing.application.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
/**
 * Runs the automatic {@code PENDING} → {@code PROCESSING} transition on a fixed cadence.
 *
 * <p>This matches the product requirement that a background job periodically promotes pending
 * orders; {@link com.example.orderprocessing.infrastructure.messaging.consumer.OrderCreatedConsumer}
 * only acknowledges {@code ORDER_CREATED} for integration and deduplication.</p>
 */
public class PendingToProcessingScheduler {

    private static final Logger log = LoggerFactory.getLogger(PendingToProcessingScheduler.class);

    private final OrderService orderService;

    public PendingToProcessingScheduler(OrderService orderService) {
        this.orderService = orderService;
    }

    @Scheduled(fixedRateString = "${app.scheduling.pending-to-processing-ms:300000}")
    void promotePendingOrders() {
        try {
            orderService.promotePendingOrdersScheduled();
        } catch (RuntimeException ex) {
            log.warn("Scheduled PENDING→PROCESSING sweep failed", ex);
        }
    }
}
