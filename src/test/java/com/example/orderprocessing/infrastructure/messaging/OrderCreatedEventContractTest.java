package com.example.orderprocessing.infrastructure.messaging;

import com.example.orderprocessing.application.event.OrderCreatedEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import com.example.orderprocessing.infrastructure.messaging.schema.VersionedJsonOrderCreatedEventSchemaRegistry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderCreatedEventContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final VersionedJsonOrderCreatedEventSchemaRegistry schemaRegistry =
            new VersionedJsonOrderCreatedEventSchemaRegistry(objectMapper, new SimpleMeterRegistry());

    @Test
    void shouldSerializeWithStableContractFields() throws Exception {
        OrderCreatedEvent event = new OrderCreatedEvent(
                2,
                "evt-1",
                "ORDER_CREATED",
                "11111111-1111-1111-1111-111111111111",
                "2026-04-04T00:00:00Z");

        String json = schemaRegistry.serialize(event);
        JsonNode node = objectMapper.readTree(json);

        assertEquals(2, node.get("schemaVersion").asInt());
        assertEquals("evt-1", node.get("eventId").asText());
        assertEquals("ORDER_CREATED", node.get("eventType").asText());
        assertEquals("11111111-1111-1111-1111-111111111111", node.get("orderId").asText());
        assertEquals("2026-04-04T00:00:00Z", node.get("occurredAt").asText());
        assertNotNull(node.get("schemaVersion"));
        assertNotNull(node.get("eventId"));
        assertNotNull(node.get("eventType"));
        assertNotNull(node.get("orderId"));
        assertNotNull(node.get("occurredAt"));
    }

    @Test
    void shouldDeserializeCanonicalProducerPayloadV2() {
        String payload = """
                {
                  "schemaVersion": 2,
                  "eventId": "evt-2",
                  "eventType": "ORDER_CREATED",
                  "orderId": "22222222-2222-2222-2222-222222222222",
                  "occurredAt": "2026-04-04T00:00:00Z"
                }
                """;

        OrderCreatedEvent event = schemaRegistry.deserialize(payload);

        assertEquals(2, event.schemaVersion());
        assertEquals("evt-2", event.eventId());
        assertEquals("ORDER_CREATED", event.eventType());
        assertEquals("22222222-2222-2222-2222-222222222222", event.orderId());
        assertEquals("2026-04-04T00:00:00Z", event.occurredAt());
    }

    @Test
    void shouldDeserializeBackwardCompatibleV1PayloadWithoutSchemaVersion() {
        String payload = """
                {
                  "eventId": "evt-v1",
                  "eventType": "ORDER_CREATED",
                  "orderId": "33333333-3333-3333-3333-333333333333",
                  "occurredAt": "2026-04-04T00:00:00Z"
                }
                """;

        OrderCreatedEvent event = schemaRegistry.deserialize(payload);

        assertEquals(1, event.schemaVersion());
        assertEquals("evt-v1", event.eventId());
    }

    @Test
    void shouldSupportForwardCompatiblePayloadWithFutureVersionAndOptionalFields() {
        String payload = """
                {
                  "schemaVersion": 7,
                  "eventId": "evt-future",
                  "eventType": "ORDER_CREATED",
                  "orderId": "44444444-4444-4444-4444-444444444444",
                  "occurredAt": "2026-04-04T00:00:00Z",
                  "customerTier": "GOLD"
                }
                """;

        OrderCreatedEvent event = schemaRegistry.deserialize(payload);

        // Unknown future versions are downgraded to the latest supported parser version.
        assertEquals(2, event.schemaVersion());
        assertEquals("evt-future", event.eventId());
    }

    @Test
    void shouldFailValidationWhenRequiredFieldMissing() {
        String payload = """
                {
                  "schemaVersion": 2,
                  "eventId": "evt-invalid",
                  "eventType": "ORDER_CREATED",
                  "occurredAt": "2026-04-04T00:00:00Z"
                }
                """;
        assertThrows(Exception.class, () -> schemaRegistry.deserialize(payload));
    }

    @Test
    void shouldFailOnNonJsonPayload() {
        assertThrows(Exception.class, () -> schemaRegistry.deserialize("not-json"));
    }
}
