package com.example.orderprocessing.infrastructure.messaging.schema;

import com.example.orderprocessing.application.event.OrderCreatedEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
/**
 * JSON schema adapter for versioned {@code ORDER_CREATED} events.
 *
 * <p><b>Architecture role:</b> infrastructure adapter implementing
 * {@link OrderCreatedEventSchemaRegistry}.</p>
 *
 * <p><b>Idempotency and resilience context:</b> normalizes schema versions and validates required
 * fields so retries process canonical event payloads; malformed payloads fail fast with
 * {@link EventSchemaValidationException}.</p>
 *
 * <p><b>Transaction boundary:</b> no owned transaction. Methods are called by DB transaction paths
 * (outbox enqueue) and Kafka transaction/consume paths.</p>
 */
public class VersionedJsonOrderCreatedEventSchemaRegistry implements OrderCreatedEventSchemaRegistry {

    private static final String SUBJECT = "ORDER_CREATED";

    private final ObjectMapper objectMapper;
    private final Counter validationErrors;
    private final MeterRegistry meterRegistry;
    private final ExternalSchemaRegistryClient schemaRegistryClient;

    /**
     * Creates a schema registry backed by Jackson and Micrometer.
     * @param objectMapper serializer/deserializer for event payloads
     * @param meterRegistry metrics registry for schema counters
     */
    @Autowired
    public VersionedJsonOrderCreatedEventSchemaRegistry(
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry,
            ExternalSchemaRegistryClient schemaRegistryClient) {
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.schemaRegistryClient = schemaRegistryClient;
        this.validationErrors = meterRegistry.counter("kafka.schema.validation.errors");
    }

    // Compatibility constructor used by unit tests that instantiate this directly.
    public VersionedJsonOrderCreatedEventSchemaRegistry(ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        this(objectMapper, meterRegistry, new PropertyBackedSchemaRegistryClient(2, 1));
    }

    @Override
    /**
     * Executes latestSchemaVersion.
     * @return operation result
     */
    public int latestSchemaVersion() {
        return schemaRegistryClient.latestVersion(SUBJECT);
    }

    @Override
    /**
     * Executes serialize.
     * @param event input argument used by this operation
     * @return operation result
     */
    public String serialize(OrderCreatedEvent event) {
        OrderCreatedEvent normalized = normalize(event);
        schemaRegistryClient.assertWriterCompatible(SUBJECT, normalized.schemaVersion());
        validate(normalized);
        recordVersion(normalized.schemaVersion());
        try {
            return objectMapper.writeValueAsString(normalized);
        } catch (JsonProcessingException ex) {
            validationErrors.increment();
            throw new EventSchemaValidationException("Failed to serialize OrderCreatedEvent", ex);
        }
    }

    @Override
    /**
     * Executes deserialize.
     * @param payload input argument used by this operation
     * @return operation result
     */
    public OrderCreatedEvent deserialize(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            Integer schemaVersion = root.hasNonNull("schemaVersion")
                    ? root.get("schemaVersion").asInt()
                    : null;
            OrderCreatedEvent event = new OrderCreatedEvent(
                    schemaRegistryClient.normalizeIncomingVersion(SUBJECT, schemaVersion),
                    readRequired(root, "eventId"),
                    readRequired(root, "eventType"),
                    readRequired(root, "orderId"),
                    readRequired(root, "occurredAt"));
            schemaRegistryClient.assertReaderCompatible(SUBJECT, event.schemaVersion());
            validate(event);
            recordVersion(event.schemaVersion());
            return event;
        } catch (EventSchemaValidationException ex) {
            validationErrors.increment();
            throw ex;
        } catch (Exception ex) {
            validationErrors.increment();
            throw new EventSchemaValidationException("Invalid OrderCreatedEvent payload", ex);
        }
    }

    @Override
    /**
     * Executes validate.
     * @param event input argument used by this operation
     */
    public void validate(OrderCreatedEvent event) {
        OrderCreatedEvent normalized = normalize(event);
        requireNotBlank(normalized.eventId(), "eventId");
        requireNotBlank(normalized.eventType(), "eventType");
        requireNotBlank(normalized.orderId(), "orderId");
        requireNotBlank(normalized.occurredAt(), "occurredAt");
    }

    private OrderCreatedEvent normalize(OrderCreatedEvent event) {
        return new OrderCreatedEvent(
                schemaRegistryClient.normalizeIncomingVersion(SUBJECT, event.schemaVersion()),
                event.eventId(),
                event.eventType(),
                event.orderId(),
                event.occurredAt());
    }

    private String readRequired(JsonNode root, String field) {
        if (!root.has(field) || root.get(field).isNull()) {
            throw new EventSchemaValidationException("Missing required field: " + field);
        }
        String value = root.get(field).asText();
        if (value == null || value.isBlank()) {
            throw new EventSchemaValidationException("Blank required field: " + field);
        }
        return value;
    }

    private void requireNotBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new EventSchemaValidationException("Blank required field: " + field);
        }
    }

    private void recordVersion(Integer version) {
        String v = String.valueOf(version == null ? latestSchemaVersion() : version);
        meterRegistry.counter(
                "kafka.event.version.distribution",
                "eventType", "ORDER_CREATED",
                "schemaVersion", v).increment();
    }
}
