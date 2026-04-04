package com.example.orderprocessing.infrastructure.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisCacheProviderTest {

    @SuppressWarnings("unchecked")
    @Test
    void getReturnsParsedValueOnCacheHit() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get("k1")).thenReturn("{\"v\":\"ok\"}");
        RedisCacheProvider provider = new RedisCacheProvider(redis, new ObjectMapper(), new SimpleMeterRegistry(), 3, 1000, 0.0);

        Optional<TestPayload> value = provider.get("k1", TestPayload.class);
        assertTrue(value.isPresent());
        assertEquals("ok", value.get().v());
    }

    @SuppressWarnings("unchecked")
    @Test
    void getReturnsEmptyOnRedisFailureFallback() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get("k1")).thenThrow(new RuntimeException("redis down"));
        RedisCacheProvider provider = new RedisCacheProvider(redis, new ObjectMapper(), new SimpleMeterRegistry(), 3, 1000, 0.0);

        Optional<TestPayload> value = provider.get("k1", TestPayload.class);
        assertTrue(value.isEmpty());
    }

    @SuppressWarnings("unchecked")
    @Test
    void putWithTtlWritesToRedis() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        RedisCacheProvider provider = new RedisCacheProvider(redis, new ObjectMapper(), new SimpleMeterRegistry(), 3, 1000, 0.0);

        provider.put("k2", new TestPayload("x"), Duration.ofSeconds(5));
        verify(ops).set(eq("k2"), any(String.class), any(Duration.class));
    }

    private record TestPayload(String v) { }
}
