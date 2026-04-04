package com.example.orderprocessing.infrastructure.messaging;

import com.example.orderprocessing.application.exception.InfrastructureException;
import com.example.orderprocessing.application.port.OrderEventPublisherPort;
import com.example.orderprocessing.infrastructure.persistence.entity.OutboxEventEntity;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class KafkaOrderEventPublisher implements OrderEventPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(KafkaOrderEventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String topic;
    private final int maxRetries;
    private final long retryBackoffMs;
    private final int failureThreshold;
    private final Duration openDuration;

    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private volatile Instant circuitOpenedAt;

    public KafkaOrderEventPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${app.kafka.order-events-topic:order.events}") String topic,
            @Value("${app.kafka.publisher.max-retries:3}") int maxRetries,
            @Value("${app.kafka.publisher.retry-backoff-ms:300}") long retryBackoffMs,
            @Value("${app.kafka.publisher.circuit-breaker.failure-threshold:5}") int failureThreshold,
            @Value("${app.kafka.publisher.circuit-breaker.open-seconds:20}") long openSeconds) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
        this.maxRetries = maxRetries;
        this.retryBackoffMs = retryBackoffMs;
        this.failureThreshold = failureThreshold;
        this.openDuration = Duration.ofSeconds(openSeconds);
    }

    @Override
    public void publish(OutboxEventEntity event) {
        if (isCircuitOpen()) {
            throw new InfrastructureException("Kafka circuit breaker is OPEN", null);
        }

        int attempts = 0;
        Exception lastException = null;

        while (attempts < maxRetries) {
            attempts++;
            try {
                kafkaTemplate.send(topic, event.getAggregateId(), event.getPayload()).get();
                consecutiveFailures.set(0);
                return;
            } catch (Exception ex) {
                lastException = ex;
                int failureCount = consecutiveFailures.incrementAndGet();
                if (failureCount >= failureThreshold) {
                    circuitOpenedAt = Instant.now();
                    log.error("Kafka circuit opened after consecutive failures={}", failureCount);
                    break;
                }
                sleepBackoff(attempts);
            }
        }

        throw new InfrastructureException("Kafka publish failed after retries for eventId=" + event.getEventId(), lastException);
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

    private void sleepBackoff(int attempts) {
        try {
            Thread.sleep(retryBackoffMs * attempts);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new InfrastructureException("Retry interrupted", ie);
        }
    }
}
