package com.example.orderprocessing.interfaces.http.error;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract: stable JSON field names for {@link ApiError} — clients and API gateways rely on this
 * envelope; changes are breaking for observability and BFFs.
 */
class ApiErrorJsonContractTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void serializesExpectedTopLevelKeys() throws Exception {
        Instant ts = Instant.parse("2026-04-01T12:00:00Z");
        ApiError err = new ApiError("NOT_FOUND", "missing", "req-1", ts);
        String json = mapper.writeValueAsString(err);
        JsonNode n = mapper.readTree(json);
        assertTrue(n.has("code"));
        assertTrue(n.has("message"));
        assertTrue(n.has("requestId"));
        assertTrue(n.has("timestamp"));
        assertEquals("NOT_FOUND", n.get("code").asText());
        assertEquals("missing", n.get("message").asText());
        assertEquals("req-1", n.get("requestId").asText());
    }
}
