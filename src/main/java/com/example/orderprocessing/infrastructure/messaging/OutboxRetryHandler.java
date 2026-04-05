package com.example.orderprocessing.infrastructure.messaging;

import com.example.orderprocessing.application.port.OutboxRepository;
import com.example.orderprocessing.infrastructure.messaging.retry.RetryPolicyStrategy;
import com.example.orderprocessing.infrastructure.persistence.entity.OutboxEntity;
import com.example.orderprocessing.infrastructure.persistence.entity.OutboxStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
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
    private final Counter transientCounter;
    private final Counter semiTransientCounter;
    private final Counter permanentCounter;
    private final DistributionSummary retryDelaySummary;
    private final int maxRetries;
    private final RetryPolicyStrategy retryPolicyStrategy;

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
                              RetryPolicyStrategy retryPolicyStrategy,
                              @Value("${app.outbox.max-retries:12}") int maxRetries) {
        this.outboxRepository = outboxRepository;
        this.transactionTemplate = transactionTemplate;
        this.retryCounter = meterRegistry.counter("outbox.retry.count");
        this.transientCounter = meterRegistry.counter("retry.classification.count", "classification", "TRANSIENT");
        this.semiTransientCounter = meterRegistry.counter("retry.classification.count", "classification", "SEMI_TRANSIENT");
        this.permanentCounter = meterRegistry.counter("retry.classification.count", "classification", "PERMANENT");
        this.retryDelaySummary = DistributionSummary.builder("retry.delay.ms").register(meterRegistry);
        this.maxRetries = maxRetries;
        this.retryPolicyStrategy = retryPolicyStrategy;
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
            RetryPolicyStrategy.RetryPlan retryPlan = retryPolicyStrategy.plan(throwable, retries);
            outboxEvent.setFailureType(retryPlan.failureType());
            outboxEvent.setLastFailureReason(retryPlan.failureReason());
            incrementClassificationCounter(retryPlan.classification().name());

            if (retries >= maxRetries || "PERMANENT".equals(retryPlan.classification().name())) {
                outboxEvent.setStatus(OutboxStatus.FAILED);
                outboxEvent.setNextAttemptAt(Instant.now().plus(3650, ChronoUnit.DAYS));
                outboxRepository.save(outboxEvent);
                log.error("Outbox max retries reached outboxId={} aggregateId={} retries={} reason={}",
                        outboxEvent.getId(), outboxEvent.getAggregateId(), retries, throwable.toString());
                return;
            }

            long delayMs = retryPlan.delayMs();
            retryDelaySummary.record(delayMs);
            outboxEvent.setStatus(OutboxStatus.FAILED);
            outboxEvent.setNextAttemptAt(Instant.now().plusMillis(delayMs));
            outboxRepository.save(outboxEvent);
            log.warn("Outbox publish failed outboxId={} aggregateId={} retries={} nextAttemptInMs={} reason={}",
                    outboxEvent.getId(), outboxEvent.getAggregateId(), retries, delayMs, throwable.toString());
        });
    }

    private void incrementClassificationCounter(String classification) {
        if ("TRANSIENT".equals(classification)) {
            transientCounter.increment();
            return;
        }
        if ("SEMI_TRANSIENT".equals(classification)) {
            semiTransientCounter.increment();
            return;
        }
        permanentCounter.increment();
    }
}
