package com.example.orderprocessing.infrastructure.messaging.producer;

import com.example.orderprocessing.application.event.OrderCreatedEvent;
import com.example.orderprocessing.application.exception.InfrastructureException;
import com.example.orderprocessing.application.port.EventPublisher;
import com.example.orderprocessing.infrastructure.messaging.schema.OrderCreatedEventSchemaRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
/**
 * Infrastructure adapter that publishes domain integration events to Kafka.
 *
 * <p>Implements asynchronous send semantics with per-order key serialization to preserve
 * event order for the same aggregate. Uses a lightweight circuit breaker so repeated broker
 * failures do not continuously amplify latency on the write path.</p>
 */
public class KafkaEventPublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaEventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OrderCreatedEventSchemaRegistry schemaRegistry;
    private final String topic;
    private final int failureThreshold;
    private final Duration openDuration;
    private final ConcurrentHashMap<String, CompletableFuture<Void>> keyPublishChains = new ConcurrentHashMap<>();

    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private volatile Instant circuitOpenedAt;

    /**
     * Creates the asynchronous Kafka event publisher.
     * @param kafkaTemplate Kafka producer template
     * @param schemaRegistry schema registry for event validation and serialization
     * @param meterRegistry metrics registry
     * @param maxAttempts max attempts for async retry wrapper
     */
    public KafkaEventPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            OrderCreatedEventSchemaRegistry schemaRegistry,
            @Value("${app.kafka.order-events-topic:order.events}") String topic,
            @Value("${app.kafka.publisher.circuit-breaker.failure-threshold:5}") int failureThreshold,
            @Value("${app.kafka.publisher.circuit-breaker.open-seconds:20}") long openSeconds) {
        this.kafkaTemplate = kafkaTemplate;
        this.schemaRegistry = schemaRegistry;
        this.topic = topic;
        this.failureThreshold = failureThreshold;
        this.openDuration = Duration.ofSeconds(openSeconds);
    }

    @Override
    /**
     * Publishes an {@code ORDER_CREATED} integration event asynchronously.
     *
     * <p>Key behaviors:</p>
     * <ul>
     *   <li>Validates and serializes payload using schema registry before send.</li>
     *   <li>Serializes sends per order key to avoid out-of-order publication for same aggregate.</li>
     *   <li>Fails fast when circuit is open after repeated broker failures.</li>
     * </ul>
     *
     * @param event event payload representing committed order creation
     * @return future that completes on broker ack or exceptionally on publish failure
     */
    public CompletableFuture<Void> publishOrderCreated(OrderCreatedEvent event) {
        if (isCircuitOpen()) {
            return CompletableFuture.failedFuture(new InfrastructureException("Kafka circuit breaker is OPEN", null));
        }

        String payload = schemaRegistry.serialize(event);
        String key = event.orderId();
        return keyPublishChains.compute(key, (k, tail) -> {
            CompletableFuture<Void> base = tail == null ? CompletableFuture.completedFuture(null) : tail.exceptionally(ex -> null);
            CompletableFuture<Void> next = base.thenCompose(ignored -> publishSingleAttempt(event, payload));
            next.whenComplete((ignored, ex) -> keyPublishChains.remove(k, next));
            return next;
        });
    }

    private boolean isCircuitOpen() {
        if (circuitOpenedAt == null) {
            return false;
        }
        if (Instant.now().isAfter(circuitOpenedAt.plus(openDuration))) {
            circuitOpenedAt = null;
            consecutiveFailures.set(0);
            return false;
        }
        return true;
    }

    private CompletableFuture<Void> publishSingleAttempt(OrderCreatedEvent event, String payload) {
        if (isCircuitOpen()) {
            return CompletableFuture.failedFuture(new InfrastructureException(
                    "Kafka circuit breaker is OPEN", null));
        }
        CompletableFuture<Void> result = new CompletableFuture<>();
        kafkaTemplate.executeInTransaction(ops -> ops.send(topic, event.orderId(), payload))
                .whenComplete((sendResult, ex) -> {
            if (ex == null) {
                consecutiveFailures.set(0);
                log.debug("Kafka publish success orderId={} eventId={}", event.orderId(), event.eventId());
                result.complete(null);
                return;
            }

            int failureCount = consecutiveFailures.incrementAndGet();
            Throwable cause = unwrap(ex);
            if (failureCount >= failureThreshold) {
                circuitOpenedAt = Instant.now();
                log.error("Kafka circuit opened after consecutive failures={} orderId={} eventId={} reason={}",
                        failureCount, event.orderId(), event.eventId(), cause.toString());
            }
            result.completeExceptionally(new InfrastructureException(
                    "Kafka publish failed for orderId=" + event.orderId(), cause));
        });
        return result;
    }

    private Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current != current.getCause()) {
            current = current.getCause();
        }
        return current;
    }

}
