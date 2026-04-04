package com.example.orderprocessing.infrastructure.messaging;

import com.example.orderprocessing.application.port.OutboxRepository;
import com.example.orderprocessing.infrastructure.persistence.entity.OutboxEntity;
import com.example.orderprocessing.infrastructure.persistence.entity.OutboxStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
/**
 * OutboxRetryHandler implements a concrete responsibility in the order processing service.
 * It is used to keep the boots the Spring runtime for the service layer explicit and maintainable in this architecture.
 */
public class OutboxRetryHandler {

    private static final Logger log = LoggerFactory.getLogger(OutboxRetryHandler.class);

    private final OutboxRepository outboxRepository;
    private final TransactionTemplate transactionTemplate;
    private final Counter retryCounter;
    private final int maxRetries;
    private final long backoffBaseMs;

    /**
     * Creates retry handling for failed outbox events.
     * @param outboxRepository outbox repository for failed-event updates
     * @param meterRegistry metrics registry
     * @param maxRetries maximum retry attempts before terminal state
     * @param baseBackoff base backoff duration
     */
    public OutboxRetryHandler(OutboxRepository outboxRepository,
                              TransactionTemplate transactionTemplate,
                              MeterRegistry meterRegistry,
                              @Value("${app.outbox.max-retries:12}") int maxRetries,
                              @Value("${app.outbox.backoff-base-ms:1000}") long backoffBaseMs) {
        this.outboxRepository = outboxRepository;
        this.transactionTemplate = transactionTemplate;
        this.retryCounter = meterRegistry.counter("outbox.retry.count");
        this.maxRetries = maxRetries;
        this.backoffBaseMs = backoffBaseMs;
    }

    /**
     * Executes handleFailure.
     * @param outboxEvent input argument used by this operation
     * @param throwable input argument used by this operation
     */
    public void handleFailure(OutboxEntity outboxEvent, Throwable throwable) {
        transactionTemplate.executeWithoutResult(status -> {
            int retries = outboxEvent.getRetryCount() == null ? 0 : outboxEvent.getRetryCount();
            retries++;
            outboxEvent.setRetryCount(retries);
            retryCounter.increment();

            if (retries >= maxRetries) {
                outboxEvent.setStatus(OutboxStatus.FAILED);
                outboxEvent.setNextAttemptAt(Instant.now().plus(3650, ChronoUnit.DAYS));
                outboxRepository.save(outboxEvent);
                log.error("Outbox max retries reached outboxId={} aggregateId={} retries={} reason={}",
                        outboxEvent.getId(), outboxEvent.getAggregateId(), retries, throwable.toString());
                return;
            }

            long delayMs = computeBackoffDelayMs(retries);
            outboxEvent.setStatus(OutboxStatus.FAILED);
            outboxEvent.setNextAttemptAt(Instant.now().plusMillis(delayMs));
            outboxRepository.save(outboxEvent);
            log.warn("Outbox publish failed outboxId={} aggregateId={} retries={} nextAttemptInMs={} reason={}",
                    outboxEvent.getId(), outboxEvent.getAggregateId(), retries, delayMs, throwable.toString());
        });
    }

    private long computeBackoffDelayMs(int retries) {
        long exp = Math.max(0, retries - 1);
        long delay = (long) (backoffBaseMs * Math.pow(2, exp));
        return Math.min(delay, 300_000L);
    }
}
