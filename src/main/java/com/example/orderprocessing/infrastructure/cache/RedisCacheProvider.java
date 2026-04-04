package com.example.orderprocessing.infrastructure.cache;

import com.example.orderprocessing.application.port.CacheProvider;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.data.redis.core.StringRedisTemplate;

@Component
public class RedisCacheProvider implements CacheProvider {

    private static final Logger log = LoggerFactory.getLogger(RedisCacheProvider.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Counter hitCounter;
    private final Counter missCounter;
    private final Counter errorCounter;

    public RedisCacheProvider(StringRedisTemplate redisTemplate, ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.hitCounter = meterRegistry.counter("cache.hit.count");
        this.missCounter = meterRegistry.counter("cache.miss.count");
        this.errorCounter = meterRegistry.counter("cache.error.count");
    }

    @Override
    public <T> Optional<T> get(String key, Class<T> type) {
        try {
            String value = redisTemplate.opsForValue().get(key);
            if (value == null) {
                missCounter.increment();
                return Optional.empty();
            }
            T parsed = objectMapper.readValue(value, type);
            hitCounter.increment();
            return Optional.of(parsed);
        } catch (JsonProcessingException ex) {
            errorCounter.increment();
            log.warn("Redis cache deserialization failure key={} reason={}", key, ex.toString());
            return Optional.empty();
        } catch (RuntimeException ex) {
            errorCounter.increment();
            log.warn("Redis cache read failure key={} reason={}", key, ex.toString());
            return Optional.empty();
        }
    }

    @Override
    public void put(String key, Object value) {
        put(key, value, null);
    }

    @Override
    public void put(String key, Object value, Duration ttl) {
        try {
            String payload = objectMapper.writeValueAsString(value);
            if (ttl == null || ttl.isZero() || ttl.isNegative()) {
                redisTemplate.opsForValue().set(key, payload);
                return;
            }
            redisTemplate.opsForValue().set(key, payload, ttl);
        } catch (JsonProcessingException ex) {
            errorCounter.increment();
            log.warn("Redis cache serialization failure key={} reason={}", key, ex.toString());
        } catch (RuntimeException ex) {
            errorCounter.increment();
            log.warn("Redis cache write failure key={} reason={}", key, ex.toString());
        }
    }

    @Override
    public void evict(String key) {
        try {
            redisTemplate.delete(key);
        } catch (RuntimeException ex) {
            errorCounter.increment();
            log.warn("Redis cache eviction failure key={} reason={}", key, ex.toString());
        }
    }
}
