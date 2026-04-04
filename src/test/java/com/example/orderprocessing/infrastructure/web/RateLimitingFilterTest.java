package com.example.orderprocessing.infrastructure.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

class RateLimitingFilterTest {

    @Test
    void allowsRequestWhenTokenBucketAllows() throws Exception {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        doReturn(1L).when(redis).execute(any(), anyList(), any(), any(), any(), any(), any());
        RateLimitingFilter filter = new RateLimitingFilter(
                redis, new ObjectMapper().findAndRegisterModules(), new SimpleMeterRegistry(), 2, 60_000);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/orders");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
    }

    @Test
    void blocksRequestWith429WhenTokenBucketRejects() throws Exception {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        doReturn(0L).when(redis).execute(any(), anyList(), any(), any(), any(), any(), any());
        RateLimitingFilter filter = new RateLimitingFilter(
                redis, new ObjectMapper().findAndRegisterModules(), new SimpleMeterRegistry(), 1, 60_000);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/orders");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertEquals(429, response.getStatus());
        assertTrue(response.getContentAsString().contains("RATE_LIMITED"));
    }
}
