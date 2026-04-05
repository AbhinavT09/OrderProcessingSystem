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
 * Applies adaptive retry policy and persists fenced failure transitions.
 *
 * <p><b>Architecture role:</b> infrastructure adapter for resilience policy execution.</p>
 *
 * <p><b>Resilience context:</b> classifies failures as transient, semi-transient, or permanent;
 * computes jittered retry delays; fails terminally when retry budget is exhausted.</p>
 *
 * <p><b>Transactional context:</b> retry metadata updates execute in DB transactions; no Kafka
 * transaction logic is owned here.</p>
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
     * Creates retry handling for failed outbox publish attempts.
     *
     * @param outboxRepository outbox repository for failed-event transitions
     * @param transactionTemplate template bound to primary transactional manager
     * @param meterRegistry metrics registry
     * @param retryPolicyStrategy adaptive retry classifier/planner
     * @param maxRetries maximum retry attempts before terminal state
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
     * Applies retry policy for one failed publish and persists next state.
     *
     * @param outboxEvent currently leased outbox row
     * @param throwable publication failure cause
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
                Instant nextAttemptAt = Instant.now().plus(3650, ChronoUnit.DAYS);
                boolean updated = outboxRepository.markFailedIfLeased(
                        outboxEvent.getId(),
                        outboxEvent.getLeaseOwner(),
                        outboxEvent.getLeaseVersion() == null ? 0L : outboxEvent.getLeaseVersion(),
                        retries,
                        retryPlan.failureType(),
                        retryPlan.failureReason(),
                        nextAttemptAt);
                if (!updated) {
                    log.warn("Skipping stale retry terminal update outboxId={} leaseOwner={}",
                            outboxEvent.getId(), outboxEvent.getLeaseOwner());
                    return;
                }
                outboxEvent.setStatus(OutboxStatus.FAILED);
                outboxEvent.setLeaseOwner(null);
                outboxEvent.setNextAttemptAt(nextAttemptAt);
                log.error("Outbox max retries reached outboxId={} aggregateId={} retries={} reason={}",
                        outboxEvent.getId(), outboxEvent.getAggregateId(), retries, throwable.toString());
                return;
            }

            long delayMs = retryPlan.delayMs();
            retryDelaySummary.record(delayMs);
            Instant nextAttemptAt = Instant.now().plusMillis(delayMs);
            boolean updated = outboxRepository.markFailedIfLeased(
                    outboxEvent.getId(),
                    outboxEvent.getLeaseOwner(),
                    outboxEvent.getLeaseVersion() == null ? 0L : outboxEvent.getLeaseVersion(),
                    retries,
                    retryPlan.failureType(),
                    retryPlan.failureReason(),
                    nextAttemptAt);
            if (!updated) {
                log.warn("Skipping stale retry update outboxId={} leaseOwner={}",
                        outboxEvent.getId(), outboxEvent.getLeaseOwner());
                return;
            }
            outboxEvent.setStatus(OutboxStatus.FAILED);
            outboxEvent.setLeaseOwner(null);
            outboxEvent.setNextAttemptAt(nextAttemptAt);
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
