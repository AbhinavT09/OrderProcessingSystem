package com.example.orderprocessing.infrastructure.messaging;

import com.example.orderprocessing.application.event.OrderCreatedEvent;
import com.example.orderprocessing.application.exception.InfrastructureException;
import com.example.orderprocessing.application.port.EventPublisher;
import com.example.orderprocessing.application.port.OutboxRepository;
import com.example.orderprocessing.infrastructure.persistence.entity.OutboxEntity;
import com.example.orderprocessing.infrastructure.persistence.entity.OutboxStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxRepository outboxRepository;
    private final EventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final int maxRetries;
    private final long backoffBaseMs;
    private final Timer publishLatencyTimer;
    private final AtomicLong pendingGaugeValue = new AtomicLong(0);
    private final AtomicLong failureGaugeValue = new AtomicLong(0);

    public OutboxPublisher(OutboxRepository outboxRepository,
                           EventPublisher eventPublisher,
                           ObjectMapper objectMapper,
                           MeterRegistry meterRegistry,
                           @Value("${app.outbox.max-retries:12}") int maxRetries,
                           @Value("${app.outbox.backoff-base-ms:1000}") long backoffBaseMs) {
        this.outboxRepository = outboxRepository;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
        this.maxRetries = maxRetries;
        this.backoffBaseMs = backoffBaseMs;
        this.publishLatencyTimer = meterRegistry.timer("outbox.publish.latency");
        Gauge.builder("outbox.pending.count", pendingGaugeValue, AtomicLong::get).register(meterRegistry);
        Gauge.builder("outbox.failure.count", failureGaugeValue, AtomicLong::get).register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${app.outbox.publisher.poll-ms:2000}")
    public void pollAndPublish() {
        refreshGauges();
        List<OutboxEntity> events = outboxRepository.findTop100ByStatusInOrderByCreatedAtAsc(
                List.of(OutboxStatus.PENDING, OutboxStatus.FAILED));
        for (OutboxEntity event : events) {
            if (!shouldAttemptNow(event)) {
                continue;
            }
            publishOne(event);
        }
        refreshGauges();
    }

    @Transactional
    void publishOne(OutboxEntity outboxEvent) {
        if (outboxRepository.existsByIdAndStatus(outboxEvent.getId(), OutboxStatus.SENT)) {
            return;
        }
        if (outboxEvent.getStatus() == OutboxStatus.SENT) {
            return;
        }
        try {
            OrderCreatedEvent event = parse(outboxEvent.getPayload());
            publishLatencyTimer.record(() -> eventPublisher.publishOrderCreated(event));
            outboxEvent.setStatus(OutboxStatus.SENT);
            outboxRepository.save(outboxEvent);
            log.info("Outbox event published outboxId={} aggregateId={} retryCount={}",
                    outboxEvent.getId(), outboxEvent.getAggregateId(), outboxEvent.getRetryCount());
        } catch (RuntimeException ex) {
            int retries = outboxEvent.getRetryCount() == null ? 0 : outboxEvent.getRetryCount();
            outboxEvent.setRetryCount(retries + 1);
            outboxEvent.setStatus(OutboxStatus.FAILED);
            outboxRepository.save(outboxEvent);
            if (outboxEvent.getRetryCount() >= maxRetries) {
                log.error("Outbox max retries reached outboxId={} aggregateId={} retries={}",
                        outboxEvent.getId(), outboxEvent.getAggregateId(), outboxEvent.getRetryCount());
            } else {
                log.warn("Outbox publish failed outboxId={} aggregateId={} retries={} reason={}",
                        outboxEvent.getId(), outboxEvent.getAggregateId(), outboxEvent.getRetryCount(), ex.toString());
            }
        }
    }

    private boolean shouldAttemptNow(OutboxEntity event) {
        if (event.getStatus() == OutboxStatus.PENDING) {
            return true;
        }
        int retries = event.getRetryCount() == null ? 0 : event.getRetryCount();
        if (retries >= maxRetries) {
            return false;
        }
        Instant updatedAt = event.getUpdatedAt() == null ? event.getCreatedAt() : event.getUpdatedAt();
        if (updatedAt == null) {
            return true;
        }
        long exp = Math.max(0, retries - 1);
        long delayMs = (long) (backoffBaseMs * Math.pow(2, exp));
        return Instant.now().isAfter(updatedAt.plus(Duration.ofMillis(Math.min(delayMs, 300_000L))));
    }

    private OrderCreatedEvent parse(String payload) {
        try {
            return objectMapper.readValue(payload, OrderCreatedEvent.class);
        } catch (JsonProcessingException ex) {
            throw new InfrastructureException("Invalid outbox payload", ex);
        }
    }

    private void refreshGauges() {
        pendingGaugeValue.set(outboxRepository.countByStatus(OutboxStatus.PENDING));
        failureGaugeValue.set(outboxRepository.countByStatus(OutboxStatus.FAILED));
    }
}
