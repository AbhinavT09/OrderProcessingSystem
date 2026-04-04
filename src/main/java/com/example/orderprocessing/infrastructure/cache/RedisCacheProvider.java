package com.example.orderprocessing.infrastructure.cache;

import com.example.orderprocessing.application.port.CacheProvider;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.data.redis.core.StringRedisTemplate;

@Component
/**
 * Infrastructure cache adapter backed by Redis.
 *
 * <p>Implements cache-aside primitives used by query services and protects callers with a
 * local circuit-breaker strategy when Redis becomes unreliable.</p>
 */
public class RedisCacheProvider implements CacheProvider {

    private static final Logger log = LoggerFactory.getLogger(RedisCacheProvider.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Counter hitCounter;
    private final Counter missCounter;
    private final Counter errorCounter;
    private final Counter redisConnectionFailures;
    private final Counter degradedModeCounter;
    private final Timer redisGetLatency;
    private final Timer redisPutLatency;
    private final Timer redisEvictLatency;
    private final int failureThreshold;
    private final Duration circuitCooldown;
    private final double maxTtlJitterPercent;
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private volatile Instant circuitOpenedAt;

    /**
     * Creates the Redis cache provider with resilience controls.
     * @param redisTemplate redis command template
     * @param objectMapper JSON serializer/deserializer
     * @param meterRegistry metrics registry
     * @param breakerThreshold failures before opening circuit breaker
     * @param cooldownMillis cooldown window while breaker is open
     */
    public RedisCacheProvider(StringRedisTemplate redisTemplate,
                              ObjectMapper objectMapper,
                              MeterRegistry meterRegistry,
                              @Value("${app.cache.redis.circuit-breaker.failure-threshold:5}") int failureThreshold,
                              @Value("${app.cache.redis.circuit-breaker.cooldown-ms:30000}") long cooldownMs,
                              @Value("${app.cache.ttl.jitter-max-percent:0.2}") double maxTtlJitterPercent) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.hitCounter = meterRegistry.counter("cache.hit.count");
        this.missCounter = meterRegistry.counter("cache.miss.count");
        this.errorCounter = meterRegistry.counter("cache.error.count");
        this.redisConnectionFailures = meterRegistry.counter("redis.connection.failures", "component", "cache");
        this.degradedModeCounter = meterRegistry.counter("cache.degraded.mode.count");
        this.redisGetLatency = meterRegistry.timer("redis.command.latency", "command", "get", "component", "cache");
        this.redisPutLatency = meterRegistry.timer("redis.command.latency", "command", "set", "component", "cache");
        this.redisEvictLatency = meterRegistry.timer("redis.command.latency", "command", "delete", "component", "cache");
        this.failureThreshold = Math.max(1, failureThreshold);
        this.circuitCooldown = Duration.ofMillis(Math.max(1000, cooldownMs));
        this.maxTtlJitterPercent = Math.max(0D, Math.min(maxTtlJitterPercent, 0.50D));
    }

    @Override
    /**
     * Reads a cached value by key and attempts typed deserialization.
     *
     * <p>On Redis or parsing failures this method fails soft (cache miss semantics), allowing
     * callers to fall back to primary storage without request failure.</p>
     *
     * @param key cache key
     * @param type expected value type
     * @return cached value when available and valid; empty otherwise
     */
    public <T> Optional<T> get(String key, Class<T> type) {
        if (isCircuitOpen()) {
            degradedModeCounter.increment();
            return Optional.empty();
        }
        try {
            String value = redisGetLatency.record(() -> redisTemplate.opsForValue().get(key));
            if (value == null) {
                missCounter.increment();
                onSuccess();
                return Optional.empty();
            }
            T parsed = objectMapper.readValue(value, type);
            hitCounter.increment();
            onSuccess();
            return Optional.of(parsed);
        } catch (JsonProcessingException ex) {
            errorCounter.increment();
            log.warn("Redis cache deserialization failure key={} reason={}", key, ex.toString());
            onFailure();
            return Optional.empty();
        } catch (RuntimeException ex) {
            errorCounter.increment();
            redisConnectionFailures.increment();
            log.warn("Redis cache read failure key={} reason={}", key, ex.toString());
            onFailure();
            return Optional.empty();
        }
    }

    @Override
    /**
     * Writes a cache entry without explicit TTL override.
     *
     * @param key cache key
     * @param value serializable value
     */
    public void put(String key, Object value) {
        put(key, value, null);
    }

    @Override
    /**
     * Writes a cache entry with optional TTL and jitter.
     *
     * <p>TTL jitter spreads expiration times to reduce synchronized cache stampedes.</p>
     *
     * @param key cache key
     * @param value serializable value
     * @param ttl desired time-to-live; null/invalid values imply non-expiring write
     */
    public void put(String key, Object value, Duration ttl) {
        if (isCircuitOpen()) {
            degradedModeCounter.increment();
            return;
        }
        try {
            String payload = objectMapper.writeValueAsString(value);
            if (ttl == null || ttl.isZero() || ttl.isNegative()) {
                redisPutLatency.record(() -> redisTemplate.opsForValue().set(key, payload));
                onSuccess();
                return;
            }
            redisPutLatency.record(() -> redisTemplate.opsForValue().set(key, payload, applyTtlJitter(ttl)));
            onSuccess();
        } catch (JsonProcessingException ex) {
            errorCounter.increment();
            log.warn("Redis cache serialization failure key={} reason={}", key, ex.toString());
            onFailure();
        } catch (RuntimeException ex) {
            errorCounter.increment();
            redisConnectionFailures.increment();
            log.warn("Redis cache write failure key={} reason={}", key, ex.toString());
            onFailure();
        }
    }

    @Override
    /**
     * Evicts a cache key.
     *
     * @param key cache key to remove
     */
    public void evict(String key) {
        if (isCircuitOpen()) {
            degradedModeCounter.increment();
            return;
        }
        try {
            redisEvictLatency.record(() -> redisTemplate.delete(key));
            onSuccess();
        } catch (RuntimeException ex) {
            errorCounter.increment();
            redisConnectionFailures.increment();
            log.warn("Redis cache eviction failure key={} reason={}", key, ex.toString());
            onFailure();
        }
    }

    private Duration applyTtlJitter(Duration baseTtl) {
        if (maxTtlJitterPercent <= 0D) {
            return baseTtl;
        }
        double spread = baseTtl.toMillis() * maxTtlJitterPercent;
        long jitter = (long) ThreadLocalRandom.current().nextDouble(-spread, spread + 1D);
        long adjustedMillis = Math.max(1000L, baseTtl.toMillis() + jitter);
        return Duration.ofMillis(adjustedMillis);
    }

    private boolean isCircuitOpen() {
        Instant openedAt = circuitOpenedAt;
        if (openedAt == null) {
            return false;
        }
        if (Instant.now().isBefore(openedAt.plus(circuitCooldown))) {
            return true;
        }
        circuitOpenedAt = null;
        consecutiveFailures.set(0);
        log.info("Redis cache circuit breaker closed after cooldown");
        return false;
    }

    private void onSuccess() {
        consecutiveFailures.set(0);
        circuitOpenedAt = null;
    }

    private void onFailure() {
        if (consecutiveFailures.incrementAndGet() >= failureThreshold && circuitOpenedAt == null) {
            circuitOpenedAt = Instant.now();
            degradedModeCounter.increment();
            log.warn("Redis cache circuit breaker opened failures={} cooldownMs={}",
                    consecutiveFailures.get(), circuitCooldown.toMillis());
        }
    }
}
