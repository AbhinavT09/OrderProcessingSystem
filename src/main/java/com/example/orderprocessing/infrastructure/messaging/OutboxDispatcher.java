package com.example.orderprocessing.infrastructure.messaging;

import com.example.orderprocessing.application.exception.InfrastructureException;
import com.example.orderprocessing.application.port.OrderEventPublisherPort;
import com.example.orderprocessing.application.port.OutboxRepositoryPort;
import com.example.orderprocessing.domain.model.OutboxStatus;
import com.example.orderprocessing.infrastructure.persistence.entity.OutboxEventEntity;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);

    private final OutboxRepositoryPort outboxRepository;
    private final OrderEventPublisherPort publisher;
    private final ObservationRegistry observationRegistry;
    private final long baseBackoffSeconds;
    private final Counter successCounter;
    private final Counter failureCounter;
    private final Counter exhaustedCounter;
    private final Timer publishTimer;

    public OutboxDispatcher(OutboxRepositoryPort outboxRepository,
                            OrderEventPublisherPort publisher,
                            ObservationRegistry observationRegistry,
                            MeterRegistry meterRegistry,
                            @Value("${app.outbox.backoff-base-seconds:5}") long baseBackoffSeconds) {
        this.outboxRepository = outboxRepository;
        this.publisher = publisher;
        this.observationRegistry = observationRegistry;
        this.baseBackoffSeconds = baseBackoffSeconds;
        this.successCounter = meterRegistry.counter("outbox.dispatch.success");
        this.failureCounter = meterRegistry.counter("outbox.dispatch.failure");
        this.exhaustedCounter = meterRegistry.counter("outbox.dispatch.exhausted");
        this.publishTimer = meterRegistry.timer("outbox.dispatch.duration");
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-ms:5000}")
    @Transactional
    public void dispatchPendingEvents() {
        List<OutboxEventEntity> events = outboxRepository.findPendingForPublish();
        for (OutboxEventEntity event : events) {
            if (!outboxRepository.claimForPublish(event.getId())) {
                continue;
            }
            dispatchSingle(event);
        }
    }

    void dispatchSingle(OutboxEventEntity event) {
        Observation.createNotStarted("outbox.dispatch", observationRegistry)
                .lowCardinalityKeyValue("eventType", event.getEventType())
                .observe(() -> publishTimer.record(() -> {
                    try {
                        publisher.publish(event);
                        event.setStatus(OutboxStatus.PUBLISHED);
                        event.setPublishedAt(Instant.now());
                        event.setLastError(null);
                        successCounter.increment();
                        log.info("Outbox dispatched eventId={} aggregateId={} eventType={}",
                                event.getEventId(), event.getAggregateId(), event.getEventType());
                    } catch (InfrastructureException ex) {
                        markFailure(event, ex);
                    } catch (Exception ex) {
                        markFailure(event, new InfrastructureException("Unexpected publisher failure", ex));
                    } finally {
                        outboxRepository.save(event);
                    }
                }));
    }

    private void markFailure(OutboxEventEntity event, InfrastructureException ex) {
        int nextAttempt = event.getAttempts() + 1;
        event.setAttempts(nextAttempt);
        event.setLastError(shortMessage(ex));

        if (nextAttempt >= event.getMaxAttempts()) {
            event.setStatus(OutboxStatus.FAILED);
            exhaustedCounter.increment();
            log.error("Outbox exhausted retries eventId={} attempts={} error={}",
                    event.getEventId(), nextAttempt, event.getLastError());
            return;
        }

        event.setStatus(OutboxStatus.PENDING);
        long backoffSeconds = computeBackoffSeconds(nextAttempt);
        event.setNextAttemptAt(Instant.now().plus(backoffSeconds, ChronoUnit.SECONDS));
        failureCounter.increment();
        log.warn("Outbox dispatch failed eventId={} attempt={} nextAttemptInSeconds={} error={}",
                event.getEventId(), nextAttempt, backoffSeconds, event.getLastError());
    }

    long computeBackoffSeconds(int attempts) {
        long exponential = (long) Math.pow(2, Math.max(0, attempts - 1));
        return Math.min(baseBackoffSeconds * exponential, 300L);
    }

    private String shortMessage(Exception ex) {
        String msg = ex.getMessage();
        if (msg == null || msg.isBlank()) {
            return ex.getClass().getSimpleName();
        }
        return msg.length() > 500 ? msg.substring(0, 500) : msg;
    }
}
