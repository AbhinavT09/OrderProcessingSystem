package com.example.orderprocessing.interfaces.http.controller;

import com.example.orderprocessing.application.service.OrderQueryService;
import com.example.orderprocessing.application.service.OrderService;
import com.example.orderprocessing.config.security.SecurityConfig;
import com.example.orderprocessing.interfaces.http.dto.CreateOrderRequest;
import com.example.orderprocessing.interfaces.http.dto.OrderItemRequest;
import com.example.orderprocessing.interfaces.http.dto.OrderResponse;
import com.example.orderprocessing.domain.order.OrderStatus;
import com.example.orderprocessing.infrastructure.web.RateLimitingFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer slice: controller mapping, validation, and JSON contracts with the real security filter
 * chain ({@link SecurityConfig}) but a pass-through {@link RateLimitingFilter} mock so requests
 * reach the controller. End-to-end JWT claim conversion remains in
 * {@link com.example.orderprocessing.api.OrderControllerIntegrationTest}.
 */
@WebMvcTest(controllers = OrderController.class)
@Import({SecurityConfig.class, OrderControllerWebMvcTest.MetricsTestConfiguration.class})
@TestPropertySource(properties = "app.security.jwt-secret=test-secret-for-integration-tests-123456")
class OrderControllerWebMvcTest {

    @TestConfiguration
    static class MetricsTestConfiguration {
        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrderService orderService;

    @MockBean
    private OrderQueryService orderQueryService;

    @MockBean
    private RateLimitingFilter rateLimitingFilter;

    @BeforeEach
    void rateLimitPassThrough() throws ServletException, IOException {
        reset(rateLimitingFilter);
        doAnswer(invocation -> {
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(rateLimitingFilter).doFilter(any(), any(), any());
    }

    private static RequestPostProcessor jwtUser(String subject) {
        return jwt()
                .jwt(j -> j.subject(subject))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Test
    void postOrders_returns201WithBody() throws Exception {
        UUID id = UUID.randomUUID();
        when(orderService.createOrder(any(), any(), eq("sub-1")))
                .thenReturn(new OrderResponse(
                        id,
                        OrderStatus.PENDING,
                        Instant.parse("2026-01-01T00:00:00Z"),
                        List.of(new OrderItemRequest("x", 1, 1.0))));

        CreateOrderRequest req = new CreateOrderRequest(List.of(new OrderItemRequest("x", 1, 1.0)));
        mockMvc.perform(post("/orders")
                        .with(jwtUser("sub-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Idempotency-Key", "k1")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void postOrders_validationError_returns400() throws Exception {
        mockMvc.perform(post("/orders")
                        .with(jwtUser("sub-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getOrder_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(orderQueryService.getById(eq(id), eq("u"), eq(false)))
                .thenReturn(new OrderResponse(id, OrderStatus.SHIPPED, Instant.parse("2026-01-02T00:00:00Z"), List.of()));

        mockMvc.perform(get("/orders/{id}", id).with(jwtUser("u")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SHIPPED"));
    }
}
