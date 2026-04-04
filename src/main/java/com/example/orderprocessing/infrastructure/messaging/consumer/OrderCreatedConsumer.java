package com.example.orderprocessing.infrastructure.messaging.consumer;

import com.example.orderprocessing.application.event.OrderCreatedEvent;
import com.example.orderprocessing.application.port.OrderRepository;
import com.example.orderprocessing.application.port.ProcessedEventRepository;
import com.example.orderprocessing.application.service.OrderMapper;
import com.example.orderprocessing.domain.order.Order;
import com.example.orderprocessing.infrastructure.messaging.DelayedProcessingNotReadyException;
import com.example.orderprocessing.infrastructure.messaging.RetryableProcessingException;
import com.example.orderprocessing.infrastructure.messaging.schema.OrderCreatedEventSchemaRegistry;
import com.example.orderprocessing.infrastructure.persistence.entity.OrderEntity;
import com.example.orderprocessing.infrastructure.persistence.entity.ProcessedEventEntity;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
/**
 * Infrastructure consumer for {@code ORDER_CREATED} events.
 *
 * <p>Consumes Kafka messages, validates schema, and applies idempotent state progression to the
 * local order aggregate projection. Retries transient failures and routes terminal failures to DLQ
 * for operator investigation.</p>
 */
public class OrderCreatedConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderCreatedConsumer.class);

    private final OrderCreatedEventSchemaRegistry schemaRegistry;
    private final OrderRepository orderRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final OrderMapper orderMapper;
    private final Counter consumeErrorCounter;
    private final Counter processedCounter;
    private final Counter retryCounter;
    private final Counter dlqCounter;
    private final DistributionSummary kafkaLagMsSummary;
    private final TransactionTemplate transactionTemplate;

    /**
     * Creates the order-created Kafka consumer.
     * @param schemaRegistry schema parser/validator
     * @param orderRepository order repository for state transitions
     * @param processedEventRepository deduplication store
     * @param meterRegistry metrics registry
     */
    public OrderCreatedConsumer(OrderCreatedEventSchemaRegistry schemaRegistry,
                                OrderRepository orderRepository,
                                ProcessedEventRepository processedEventRepository,
                                OrderMapper orderMapper,
                                MeterRegistry meterRegistry,
                                TransactionTemplate transactionTemplate) {
        this.schemaRegistry = schemaRegistry;
        this.orderRepository = orderRepository;
        this.processedEventRepository = processedEventRepository;
        this.orderMapper = orderMapper;
        this.transactionTemplate = transactionTemplate;
        this.consumeErrorCounter = meterRegistry.counter("kafka.consumer.errors");
        this.processedCounter = meterRegistry.counter("kafka.consumer.processed.count");
        this.retryCounter = meterRegistry.counter("kafka.consumer.retry.count");
        this.dlqCounter = meterRegistry.counter("kafka.consumer.dlq.count");
        this.kafkaLagMsSummary = DistributionSummary.builder("kafka.consumer.lag.ms")
                .publishPercentiles(0.95, 0.99)
                .register(meterRegistry);
    }

    @RetryableTopic(
            attempts = "${app.kafka.delayed-processing-attempts:12}",
            backoff = @Backoff(
                    delayExpression = "${app.kafka.processing-delay-ms:300000}",
                    multiplierExpression = "${app.kafka.processing-delay-multiplier:2.0}",
                    maxDelayExpression = "${app.kafka.processing-delay-max-ms:3600000}"
            ),
            include = {
                    DelayedProcessingNotReadyException.class,
                    RetryableProcessingException.class
            },
            dltStrategy = DltStrategy.FAIL_ON_ERROR
    )
    @KafkaListener(topics = "${app.kafka.order-events-topic:order.events}", groupId = "${app.kafka.consumer-group:order-processing-group}")
    /**
     * Processes one Kafka record with retry-aware, idempotent semantics.
     *
     * <p>Key behaviors:</p>
     * <ul>
     *   <li>Rejects premature processing when event delay window has not elapsed.</li>
     *   <li>Executes processing transactionally with processed-event dedup marker.</li>
     *   <li>Acknowledges duplicates safely; retries transient DB and timing failures.</li>
     * </ul>
     *
     * @param payload serialized order-created event
     * @param acknowledgment manual acknowledgment handle for offset commits
     */
    public void consume(String payload, Acknowledgment acknowledgment) {
        try {
            OrderCreatedEvent event = parse(payload);
            Instant occurredAt = Instant.parse(event.occurredAt());
            kafkaLagMsSummary.record(Math.max(0, Instant.now().toEpochMilli() - occurredAt.toEpochMilli()));
            if (Instant.now().isBefore(occurredAt.plus(5, ChronoUnit.MINUTES))) {
                throw new DelayedProcessingNotReadyException(
                        "OrderCreated event is not ready for processing yet: " + event.eventId());
            }
            processEventInTransaction(event);
            processedCounter.increment();
            acknowledgment.acknowledge();
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            if (isDuplicateProcessedMarkerViolation(ex)) {
                // Unique processed-event marker race: treat as already processed and ack.
                log.info("Processed marker race handled as duplicate reason={}", ex.toString());
                acknowledgment.acknowledge();
                return;
            }
            consumeErrorCounter.increment();
            retryCounter.increment();
            throw new RetryableProcessingException("Data integrity violation while processing event", ex);
        } catch (DataAccessException ex) {
            consumeErrorCounter.increment();
            retryCounter.increment();
            throw new RetryableProcessingException("Transient DB failure while processing event", ex);
        } catch (DelayedProcessingNotReadyException | RetryableProcessingException ex) {
            consumeErrorCounter.increment();
            retryCounter.increment();
            throw ex;
        } catch (RuntimeException ex) {
            consumeErrorCounter.increment();
            throw ex;
        }
    }

    @DltHandler
    /**
     * Handles records that exhausted retries and were sent to dead-letter topic.
     *
     * <p>Publishes failure telemetry and structured logs for operational triage. Attempts best-effort
     * event parsing to include event/order identifiers in diagnostics.</p>
     *
     * @param payload dead-letter payload
     * @param topic source dead-letter topic
     * @param partition source partition
     * @param offset source offset
     * @param exceptionMessage broker-provided terminal failure context
     */
    public void onDlt(
            String payload,
            @Header(value = KafkaHeaders.RECEIVED_TOPIC, required = false) String topic,
            @Header(value = KafkaHeaders.RECEIVED_PARTITION, required = false) Integer partition,
            @Header(value = KafkaHeaders.OFFSET, required = false) Long offset,
            @Header(value = KafkaHeaders.DLT_EXCEPTION_MESSAGE, required = false) String exceptionMessage) {
        dlqCounter.increment();
        String eventId = "unknown";
        String orderId = "unknown";
        try {
            OrderCreatedEvent event = parse(payload);
            eventId = event.eventId();
            orderId = event.orderId();
        } catch (RuntimeException ignored) {
            // keep unknown identifiers for malformed payloads
        }
        log.error("OrderCreatedEvent routed to DLQ topic={} partition={} offset={} eventId={} orderId={} reason={} payload={}",
                topic, partition, offset, eventId, orderId, exceptionMessage, payload);
    }

    private void persistProcessed(OrderCreatedEvent event) {
        ProcessedEventEntity processed = new ProcessedEventEntity();
        processed.setEventId(event.eventId());
        processed.setEventType(event.eventType());
        processed.setProcessedAt(Instant.now());
        processedEventRepository.save(processed);
    }

    private void processEventInTransaction(OrderCreatedEvent event) {
        transactionTemplate.executeWithoutResult(status -> {
            if (processedEventRepository.existsByEventId(event.eventId())) {
                log.info("Skipping already processed event eventId={} orderId={}", event.eventId(), event.orderId());
                return;
            }

            Optional<OrderEntity> entity = orderRepository.findById(UUID.fromString(event.orderId()));
            if (entity.isEmpty()) {
                log.warn("Order not found for consumed event eventId={} orderId={}", event.eventId(), event.orderId());
                persistProcessed(event);
                return;
            }

            Order order = orderMapper.toDomain(entity.get());
            if (order.promotePendingToProcessing()) {
                orderRepository.save(orderMapper.toEntity(order));
                log.info("Processed OrderCreated event; order promoted to PROCESSING orderId={} eventId={}",
                        event.orderId(), event.eventId());
            } else {
                log.info("Order already progressed, skipping transition orderId={} eventId={}",
                        event.orderId(), event.eventId());
            }

            persistProcessed(event);
        });
    }

    private OrderCreatedEvent parse(String payload) {
        return schemaRegistry.deserialize(payload);
    }

    private boolean isDuplicateProcessedMarkerViolation(org.springframework.dao.DataIntegrityViolationException ex) {
        String message = ex.getMostSpecificCause() != null
                ? ex.getMostSpecificCause().getMessage()
                : ex.getMessage();
        if (message == null) {
            return false;
        }
        String normalized = message.toLowerCase();
        return normalized.contains("uk_processed_event_id")
                || (normalized.contains("processed_events") && normalized.contains("eventid"))
                || normalized.contains("duplicate") && normalized.contains("eventid");
    }
}
