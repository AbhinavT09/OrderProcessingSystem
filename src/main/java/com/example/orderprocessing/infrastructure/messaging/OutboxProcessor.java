package com.example.orderprocessing.infrastructure.messaging;

import com.example.orderprocessing.application.event.OrderCreatedEvent;
import com.example.orderprocessing.application.port.EventPublisher;
import com.example.orderprocessing.application.port.OutboxRepository;
import com.example.orderprocessing.infrastructure.messaging.schema.OrderCreatedEventSchemaRegistry;
import com.example.orderprocessing.infrastructure.persistence.entity.OutboxEntity;
import com.example.orderprocessing.infrastructure.persistence.entity.OutboxStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
/**
 * Infrastructure component that publishes claimed outbox rows.
 *
 * <p>Parses payloads, invokes asynchronous event publisher, and transitions rows to SENT or retry
 * states. This component is part of the reliable outbox delivery pipeline.</p>
 *
 * <p><b>Idempotency and resilience context:</b> completion and failure callbacks use lease-fenced
 * repository transitions, so stale callbacks cannot corrupt newer claims. In-flight publish count
 * is bounded with a semaphore.</p>
 *
 * <p><b>Transactional context:</b> Kafka send is async and may run in producer-managed Kafka
 * transactions; status transitions to {@code SENT}/{@code FAILED} run in DB transaction templates.</p>
 */
public class OutboxProcessor {

    private final EventPublisher eventPublisher;
    private final OutboxRepository outboxRepository;
    private final OrderCreatedEventSchemaRegistry schemaRegistry;
    private final TransactionTemplate transactionTemplate;
    private final OutboxRetryHandler retryHandler;
    private final Timer publishLatencyTimer;
    private final DistributionSummary outboxLagSummary;
    private final Counter publishRateCounter;
    private final Semaphore publishInFlightSemaphore;

    /**
     * Creates an outbox batch processor.
     * @param eventPublisher publisher for domain events
     * @param outboxRepository outbox repository for state updates
     * @param meterRegistry metrics registry
     */
    public OutboxProcessor(EventPublisher eventPublisher,
                           OutboxRepository outboxRepository,
                           OrderCreatedEventSchemaRegistry schemaRegistry,
                           TransactionTemplate transactionTemplate,
                           OutboxRetryHandler retryHandler,
                           MeterRegistry meterRegistry,
                           @Value("${app.outbox.publisher.max-in-flight:16}") int maxInFlightPublishes) {
        this.eventPublisher = eventPublisher;
        this.outboxRepository = outboxRepository;
        this.schemaRegistry = schemaRegistry;
        this.transactionTemplate = transactionTemplate;
        this.retryHandler = retryHandler;
        this.publishLatencyTimer = meterRegistry.timer("outbox.publish.latency");
        this.outboxLagSummary = DistributionSummary.builder("outbox.lag").register(meterRegistry);
        this.publishRateCounter = meterRegistry.counter("outbox.publish.rate");
        this.publishInFlightSemaphore = new Semaphore(Math.max(1, maxInFlightPublishes));
    }

    /**
     * Processes a claimed outbox batch.
     *
     * <p>Each row is handled independently to isolate failures and maximize forward progress.</p>
     *
     * @param claimed rows leased for the current processing window
     */
    public CompletableFuture<Void> processBatch(List<OutboxEntity> claimed) {
        List<CompletableFuture<Void>> futures = claimed.stream().map(this::publishOne).toList();
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    private CompletableFuture<Void> publishOne(OutboxEntity outboxEvent) {
        if (outboxEvent.getStatus() == OutboxStatus.SENT) {
            return CompletableFuture.completedFuture(null);
        }
        try {
            publishInFlightSemaphore.acquireUninterruptibly();
            OrderCreatedEvent event = parse(outboxEvent.getPayload());
            Timer.Sample sample = Timer.start();
            CompletableFuture<Void> publishFuture = eventPublisher.publishOrderCreated(event);
            return publishFuture.whenComplete((ignored, ex) -> {
                sample.stop(publishLatencyTimer);
                if (ex == null) {
                    onPublishSuccess(outboxEvent);
                } else {
                    retryHandler.handleFailure(outboxEvent, ex);
                }
                publishInFlightSemaphore.release();
            });
        } catch (RuntimeException ex) {
            publishInFlightSemaphore.release();
            retryHandler.handleFailure(outboxEvent, ex);
            return CompletableFuture.failedFuture(ex);
        }
    }

    private void onPublishSuccess(OutboxEntity outboxEvent) {
        transactionTemplate.executeWithoutResult(status -> {
            publishRateCounter.increment();
            outboxLagSummary.record(Math.max(0, Duration.between(outboxEvent.getCreatedAt(), Instant.now()).toMillis()));
            boolean updated = outboxRepository.markSentIfLeased(
                    outboxEvent.getId(),
                    outboxEvent.getLeaseOwner(),
                    outboxEvent.getLeaseVersion() == null ? 0L : outboxEvent.getLeaseVersion(),
                    Instant.now());
            if (!updated) {
                // Another worker reclaimed this row after lease expiry; avoid stale overwrite.
                return;
            }
            outboxEvent.setStatus(OutboxStatus.SENT);
            outboxEvent.setLeaseOwner(null);
            outboxEvent.setFailureType(null);
            outboxEvent.setLastFailureReason(null);
            outboxEvent.setNextAttemptAt(Instant.now());
        });
    }

    private OrderCreatedEvent parse(String payload) {
        return schemaRegistry.deserialize(payload);
    }
}
