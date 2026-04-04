package com.example.orderprocessing.infrastructure.messaging;

import com.example.orderprocessing.application.event.OrderCreatedEvent;
import com.example.orderprocessing.application.exception.InfrastructureException;
import com.example.orderprocessing.application.port.EventPublisher;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class KafkaEventPublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaEventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String topic;
    private final int maxRetries;
    private final long retryBackoffMs;
    private final int failureThreshold;
    private final Duration openDuration;

    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private volatile Instant circuitOpenedAt;

    public KafkaEventPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${app.kafka.order-events-topic:order.events}") String topic,
            @Value("${app.kafka.publisher.max-retries:3}") int maxRetries,
            @Value("${app.kafka.publisher.retry-backoff-ms:300}") long retryBackoffMs,
            @Value("${app.kafka.publisher.circuit-breaker.failure-threshold:5}") int failureThreshold,
            @Value("${app.kafka.publisher.circuit-breaker.open-seconds:20}") long openSeconds) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.topic = topic;
        this.maxRetries = maxRetries;
        this.retryBackoffMs = retryBackoffMs;
        this.failureThreshold = failureThreshold;
        this.openDuration = Duration.ofSeconds(openSeconds);
    }

    @Override
    public void publishOrderCreated(OrderCreatedEvent event) {
        if (isCircuitOpen()) {
            throw new InfrastructureException("Kafka circuit breaker is OPEN", null);
        }

        String payload = toJson(event);
        int attempts = 0;
        Exception lastException = null;

        while (attempts < maxRetries) {
            attempts++;
            try {
                // Partition by order_id using Kafka key.
                kafkaTemplate.send(topic, event.orderId(), payload).get();
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

        throw new InfrastructureException("Kafka publish failed after retries for orderId=" + event.orderId(), lastException);
    }

    private String toJson(OrderCreatedEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            throw new InfrastructureException("Failed to serialize OrderCreatedEvent", ex);
        }
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
            long delay = (long) (retryBackoffMs * Math.pow(2, Math.max(0, attempts - 1)));
            Thread.sleep(Math.min(delay, 10000L));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new InfrastructureException("Retry interrupted", ie);
        }
    }
}
