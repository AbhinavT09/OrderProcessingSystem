package com.example.orderprocessing;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class OrderControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldCreateAndFetchOrder() throws Exception {
        String payload = """
                {
                  "items": [
                    {"productName": "Phone", "quantity": 1, "price": 699.0},
                    {"productName": "Case", "quantity": 2, "price": 29.0}
                  ]
                }
                """;

        MvcResult createResult = mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();

        JsonNode body = objectMapper.readTree(createResult.getResponse().getContentAsString());
        String orderId = body.get("id").asText();

        mockMvc.perform(get("/orders/{id}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId))
                .andExpect(jsonPath("$.items.length()").value(2));
    }

    @Test
    void shouldUpdateOrderStatus() throws Exception {
        String createPayload = """
                {
                  "items": [
                    {"productName": "Book", "quantity": 1, "price": 19.0}
                  ]
                }
                """;

        MvcResult createResult = mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload))
                .andExpect(status().isCreated())
                .andReturn();

        String orderId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(patch("/orders/{id}/status", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SHIPPED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SHIPPED"));
    }

    @Test
    void shouldListOrdersByStatus() throws Exception {
        String payload = """
                {
                  "items": [
                    {"productName": "Pen", "quantity": 3, "price": 3.5}
                  ]
                }
                """;

        MvcResult created1 = mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn();

        MvcResult created2 = mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn();

        String orderId1 = objectMapper.readTree(created1.getResponse().getContentAsString()).get("id").asText();
        String orderId2 = objectMapper.readTree(created2.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(patch("/orders/{id}/status", orderId2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DELIVERED\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/orders?status=PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + orderId1 + "')]").exists())
                .andExpect(jsonPath("$[?(@.id=='" + orderId2 + "')]").doesNotExist());
    }

    @Test
    void shouldCancelPendingOrderAndRejectCancellationForNonPending() throws Exception {
        String payload = """
                {
                  "items": [
                    {"productName": "Laptop", "quantity": 1, "price": 999.0}
                  ]
                }
                """;

        MvcResult createResult = mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn();

        String orderId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(patch("/orders/{id}/cancel", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(patch("/orders/{id}/cancel", orderId))
                .andExpect(status().isBadRequest());
    }
}
