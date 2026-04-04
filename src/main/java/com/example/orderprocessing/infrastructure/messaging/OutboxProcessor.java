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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
/**
 * OutboxProcessor implements a concrete responsibility in the order processing service.
 * It is used to keep the boots the Spring runtime for the service layer explicit and maintainable in this architecture.
 */
public class OutboxProcessor {

    private final EventPublisher eventPublisher;
    private final OutboxRepository outboxRepository;
    private final OrderCreatedEventSchemaRegistry schemaRegistry;
    private final TransactionTemplate transactionTemplate;
    private final OutboxRetryHandler retryHandler;
    private final long inFlightLeaseMs;
    private final Timer publishLatencyTimer;
    private final DistributionSummary outboxLagSummary;
    private final Counter publishRateCounter;

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
                           @Value("${app.outbox.publisher.in-flight-lease-ms:30000}") long inFlightLeaseMs) {
        this.eventPublisher = eventPublisher;
        this.outboxRepository = outboxRepository;
        this.schemaRegistry = schemaRegistry;
        this.transactionTemplate = transactionTemplate;
        this.retryHandler = retryHandler;
        this.inFlightLeaseMs = Math.max(1000, inFlightLeaseMs);
        this.publishLatencyTimer = meterRegistry.timer("outbox.publish.latency");
        this.outboxLagSummary = DistributionSummary.builder("outbox.lag").register(meterRegistry);
        this.publishRateCounter = meterRegistry.counter("outbox.publish.rate");
    }

    /**
     * Executes processBatch.
     * @param claimed input argument used by this operation
     */
    public void processBatch(List<OutboxEntity> claimed) {
        for (OutboxEntity event : claimed) {
            publishOne(event);
        }
    }

    private void publishOne(OutboxEntity outboxEvent) {
        if (outboxEvent.getStatus() == OutboxStatus.SENT) {
            return;
        }
        try {
            OrderCreatedEvent event = parse(outboxEvent.getPayload());
            // Mark as leased while async publish is in-flight to reduce duplicate claims.
            outboxEvent.setStatus(OutboxStatus.FAILED);
            outboxEvent.setNextAttemptAt(Instant.now().plusMillis(inFlightLeaseMs));
            outboxRepository.save(outboxEvent);
            Timer.Sample sample = Timer.start();
            CompletableFuture<Void> publishFuture = eventPublisher.publishOrderCreated(event);
            publishFuture.whenComplete((ignored, ex) -> {
                sample.stop(publishLatencyTimer);
                if (ex == null) {
                    onPublishSuccess(outboxEvent);
                } else {
                    retryHandler.handleFailure(outboxEvent, ex);
                }
            });
        } catch (RuntimeException ex) {
            retryHandler.handleFailure(outboxEvent, ex);
        }
    }

    private void onPublishSuccess(OutboxEntity outboxEvent) {
        transactionTemplate.executeWithoutResult(status -> {
            publishRateCounter.increment();
            outboxLagSummary.record(Math.max(0, Duration.between(outboxEvent.getCreatedAt(), Instant.now()).toMillis()));
            outboxEvent.setStatus(OutboxStatus.SENT);
            outboxEvent.setNextAttemptAt(Instant.now());
            outboxRepository.save(outboxEvent);
        });
    }

    private OrderCreatedEvent parse(String payload) {
        return schemaRegistry.deserialize(payload);
    }
}
