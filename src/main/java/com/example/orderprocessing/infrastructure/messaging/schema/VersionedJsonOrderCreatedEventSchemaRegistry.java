package com.example.orderprocessing.infrastructure.messaging.schema;

import com.example.orderprocessing.application.event.OrderCreatedEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
/**
 * VersionedJsonOrderCreatedEventSchemaRegistry implements a concrete responsibility in the order processing service.
 * It is used to keep the boots the Spring runtime for the service layer explicit and maintainable in this architecture.
 */
public class VersionedJsonOrderCreatedEventSchemaRegistry implements OrderCreatedEventSchemaRegistry {

    public static final int SCHEMA_V1 = 1;
    public static final int SCHEMA_V2 = 2;

    private final ObjectMapper objectMapper;
    private final Counter validationErrors;
    private final MeterRegistry meterRegistry;

    /**
     * Creates a schema registry backed by Jackson and Micrometer.
     * @param objectMapper serializer/deserializer for event payloads
     * @param meterRegistry metrics registry for schema counters
     */
    public VersionedJsonOrderCreatedEventSchemaRegistry(ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.validationErrors = meterRegistry.counter("kafka.schema.validation.errors");
    }

    @Override
    /**
     * Executes latestSchemaVersion.
     * @return operation result
     */
    public int latestSchemaVersion() {
        return SCHEMA_V2;
    }

    @Override
    /**
     * Executes serialize.
     * @param event input argument used by this operation
     * @return operation result
     */
    public String serialize(OrderCreatedEvent event) {
        OrderCreatedEvent normalized = normalize(event);
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
                    : SCHEMA_V1;
            OrderCreatedEvent event = new OrderCreatedEvent(
                    normalizeSchemaVersion(schemaVersion),
                    readRequired(root, "eventId"),
                    readRequired(root, "eventType"),
                    readRequired(root, "orderId"),
                    readRequired(root, "occurredAt"));
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
                normalizeSchemaVersion(event.schemaVersion()),
                event.eventId(),
                event.eventType(),
                event.orderId(),
                event.occurredAt());
    }

    private int normalizeSchemaVersion(Integer schemaVersion) {
        int version = schemaVersion == null ? SCHEMA_V1 : schemaVersion;
        if (version <= 0) {
            throw new EventSchemaValidationException("schemaVersion must be >= 1");
        }
        // Forward compatibility fallback: accept unknown future versions with latest parser rules.
        return Math.min(version, latestSchemaVersion());
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
        String v = String.valueOf(version == null ? SCHEMA_V1 : version);
        meterRegistry.counter(
                "kafka.event.version.distribution",
                "eventType", "ORDER_CREATED",
                "schemaVersion", v).increment();
    }
}
