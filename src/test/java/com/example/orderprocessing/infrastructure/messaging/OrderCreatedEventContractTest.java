package com.example.orderprocessing.infrastructure.messaging;

import com.example.orderprocessing.application.event.OrderCreatedEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderCreatedEventContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldSerializeWithStableContractFields() throws Exception {
        OrderCreatedEvent event = new OrderCreatedEvent(
                "evt-1",
                "ORDER_CREATED",
                "11111111-1111-1111-1111-111111111111",
                "2026-04-04T00:00:00Z");

        String json = objectMapper.writeValueAsString(event);
        JsonNode node = objectMapper.readTree(json);

        assertEquals("evt-1", node.get("eventId").asText());
        assertEquals("ORDER_CREATED", node.get("eventType").asText());
        assertEquals("11111111-1111-1111-1111-111111111111", node.get("orderId").asText());
        assertEquals("2026-04-04T00:00:00Z", node.get("occurredAt").asText());
        assertNotNull(node.get("eventId"));
        assertNotNull(node.get("eventType"));
        assertNotNull(node.get("orderId"));
        assertNotNull(node.get("occurredAt"));
    }

    @Test
    void shouldDeserializeCanonicalProducerPayload() throws Exception {
        String payload = """
                {
                  "eventId": "evt-2",
                  "eventType": "ORDER_CREATED",
                  "orderId": "22222222-2222-2222-2222-222222222222",
                  "occurredAt": "2026-04-04T00:00:00Z"
                }
                """;

        OrderCreatedEvent event = objectMapper.readValue(payload, OrderCreatedEvent.class);

        assertEquals("evt-2", event.eventId());
        assertEquals("ORDER_CREATED", event.eventType());
        assertEquals("22222222-2222-2222-2222-222222222222", event.orderId());
        assertEquals("2026-04-04T00:00:00Z", event.occurredAt());
    }

    @Test
    void shouldFailOnNonJsonPayload() {
        assertThrows(Exception.class, () -> objectMapper.readValue("not-json", OrderCreatedEvent.class));
    }
}
