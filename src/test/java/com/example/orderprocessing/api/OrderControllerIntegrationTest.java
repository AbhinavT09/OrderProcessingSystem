package com.example.orderprocessing.api;

import com.example.orderprocessing.application.port.EventPublisher;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.security.jwt-secret=test-secret-for-integration-tests-123456",
        "app.security.rate-limit.requests=1000",
        "spring.data.redis.sentinel.master=test-master",
        "spring.data.redis.sentinel.nodes=localhost:26379",
        "spring.kafka.listener.auto-startup=false",
        "spring.kafka.admin.auto-create=false",
        "spring.task.scheduling.enabled=false"
})
@AutoConfigureMockMvc
class OrderControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private EventPublisher eventPublisher;

    /**
     * Test JWT with {@code sub} for ownership checks and explicit roles (mock post-processor does not
     * run claims through {@link com.example.orderprocessing.infrastructure.security.RoleClaimJwtAuthenticationConverter}).
     */
    private static RequestPostProcessor jwtUser(String subject) {
        return SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(j -> j.subject(subject))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    private static RequestPostProcessor jwtAdmin(String subject) {
        return SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(j -> j.subject(subject))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    @Test
    void shouldRequireAuthentication() throws Exception {
        mockMvc.perform(get("/orders"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldCreateAndFetchOrderWhenAuthorized() throws Exception {
        String body = """
                {
                  "items": [
                    { "productName": "Laptop", "quantity": 1, "price": 1200.0 }
                  ]
                }
                """;

        MvcResult createResult = mockMvc.perform(post("/orders")
                        .with(jwtUser("customer-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Idempotency-Key", "idem-create-order-1")
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();

        JsonNode created = objectMapper.readTree(createResult.getResponse().getContentAsString());
        String id = created.get("id").asText();

        mockMvc.perform(get("/orders/{id}", id)
                        .with(jwtUser("customer-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
    }

    @Test
    void shouldRejectStatusUpdateForNonAdminRole() throws Exception {
        String body = """
                {
                  "items": [
                    { "productName": "Headset", "quantity": 1, "price": 100.0 }
                  ]
                }
                """;
        MvcResult createResult = mockMvc.perform(post("/orders")
                        .with(jwtUser("customer-2"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Idempotency-Key", "idem-create-order-2")
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode created = objectMapper.readTree(createResult.getResponse().getContentAsString());
        String id = created.get("id").asText();

        String patchBody = """
                {
                  "status": "PROCESSING",
                  "version": 0
                }
                """;

        mockMvc.perform(patch("/orders/{id}/status", id)
                        .with(jwtUser("customer-2"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patchBody))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldValidateHeaderAndPayload() throws Exception {
        mockMvc.perform(post("/orders")
                        .with(jwtUser("customer-val"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Idempotency-Key", "x".repeat(129))
                        .content("{\"items\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void shouldReturnSameOrderForSameIdempotencyKey() throws Exception {
        String body = """
                {
                  "items": [
                    { "productName": "Desk Lamp", "quantity": 1, "price": 30.0 }
                  ]
                }
                """;

        MvcResult first = mockMvc.perform(post("/orders")
                        .with(jwtUser("customer-idem"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Idempotency-Key", "idem-integration-1")
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        MvcResult second = mockMvc.perform(post("/orders")
                        .with(jwtUser("customer-idem"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Idempotency-Key", "idem-integration-1")
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        String firstId = objectMapper.readTree(first.getResponse().getContentAsString()).get("id").asText();
        String secondId = objectMapper.readTree(second.getResponse().getContentAsString()).get("id").asText();
        org.junit.jupiter.api.Assertions.assertEquals(firstId, secondId);
    }

    @Test
    void shouldCancelOwnPendingOrder() throws Exception {
        String body = """
                {
                  "items": [ { "productName": "Mug", "quantity": 1, "price": 5.0 } ]
                }
                """;
        MvcResult createResult = mockMvc.perform(post("/orders")
                        .with(jwtUser("owner-a"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Idempotency-Key", "idem-cancel-own")
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();

        String id = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(patch("/orders/{id}/cancel", id)
                        .with(jwtUser("owner-a")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void shouldRejectCancelWhenDifferentUser() throws Exception {
        String body = """
                {
                  "items": [ { "productName": "Pen", "quantity": 1, "price": 2.0 } ]
                }
                """;
        MvcResult createResult = mockMvc.perform(post("/orders")
                        .with(jwtUser("owner-b"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Idempotency-Key", "idem-cancel-other")
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        String id = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(patch("/orders/{id}/cancel", id)
                        .with(jwtUser("intruder")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void shouldAllowAdminToCancelAnotherUsersOrder() throws Exception {
        String body = """
                {
                  "items": [ { "productName": "Notebook", "quantity": 1, "price": 8.0 } ]
                }
                """;
        MvcResult createResult = mockMvc.perform(post("/orders")
                        .with(jwtUser("owner-c"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Idempotency-Key", "idem-cancel-admin")
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        String id = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(patch("/orders/{id}/cancel", id)
                        .with(jwtAdmin("ops-admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void shouldReturnNotFoundWhenFetchingAnotherUsersOrder() throws Exception {
        String body = """
                { "items": [ { "productName": "Book", "quantity": 1, "price": 12.0 } ] }
                """;
        MvcResult create = mockMvc.perform(post("/orders")
                        .with(jwtUser("read-owner-a"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Idempotency-Key", "idem-read-cross")
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        String id = objectMapper.readTree(create.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(get("/orders/{id}", id).with(jwtUser("read-owner-b")))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldAllowAdminToFetchAnyOrderById() throws Exception {
        String body = """
                { "items": [ { "productName": "AdminRead", "quantity": 1, "price": 3.0 } ] }
                """;
        MvcResult create = mockMvc.perform(post("/orders")
                        .with(jwtUser("customer-for-admin-read"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Idempotency-Key", "idem-admin-read-by-id")
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        String id = objectMapper.readTree(create.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(get("/orders/{id}", id).with(jwtAdmin("super-reader")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
    }

    @Test
    void shouldListOnlyOwnOrdersForNonAdmin() throws Exception {
        String bodyA = """
                { "items": [ { "productName": "OnlyMineA", "quantity": 1, "price": 1.0 } ] }
                """;
        mockMvc.perform(post("/orders")
                        .with(jwtUser("list-scope-a"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Idempotency-Key", "idem-list-scope-a")
                        .content(bodyA))
                .andExpect(status().isCreated());

        String bodyB = """
                { "items": [ { "productName": "OtherUserB", "quantity": 1, "price": 2.0 } ] }
                """;
        mockMvc.perform(post("/orders")
                        .with(jwtUser("list-scope-b"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Idempotency-Key", "idem-list-scope-b")
                        .content(bodyB))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/orders").with(jwtUser("list-scope-a")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].items[0].productName").value("OnlyMineA"));
    }

    @Test
    void shouldListOrdersFilteredByPendingStatus() throws Exception {
        String body = """
                { "items": [ { "productName": "FilteredPending", "quantity": 2, "price": 9.5 } ] }
                """;
        MvcResult create = mockMvc.perform(post("/orders")
                        .with(jwtUser("filter-by-status-user"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Idempotency-Key", "idem-list-filter-pending")
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();

        String id = objectMapper.readTree(create.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(get("/orders").param("status", "PENDING").with(jwtUser("filter-by-status-user")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[0].items[0].productName").value("FilteredPending"));
    }

    @Test
    void shouldAllowAdminToAdvanceStatusToShipped() throws Exception {
        String body = """
                { "items": [ { "productName": "ShipCandidate", "quantity": 1, "price": 40.0 } ] }
                """;
        MvcResult create = mockMvc.perform(post("/orders")
                        .with(jwtUser("ship-candidate-owner"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Idempotency-Key", "idem-admin-ship-1")
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();

        String id = objectMapper.readTree(create.getResponse().getContentAsString()).get("id").asText();

        String patchBody = """
                {
                  "status": "SHIPPED",
                  "version": 0
                }
                """;

        mockMvc.perform(patch("/orders/{id}/status", id)
                        .with(jwtAdmin("fulfillment-admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patchBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.status").value("SHIPPED"));
    }
}
