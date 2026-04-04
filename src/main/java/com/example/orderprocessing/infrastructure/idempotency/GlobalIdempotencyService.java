package com.example.orderprocessing.infrastructure.idempotency;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class GlobalIdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(GlobalIdempotencyService.class);

    private final StringRedisTemplate redisTemplate;
    private final Duration lockTtl;
    private final Duration completedTtl;
    private final boolean enabled;

    public GlobalIdempotencyService(
            StringRedisTemplate redisTemplate,
            @Value("${app.multi-region.global-idempotency.enabled:true}") boolean enabled,
            @Value("${app.multi-region.global-idempotency.lock-ttl-seconds:60}") long lockTtlSeconds,
            @Value("${app.multi-region.global-idempotency.completed-ttl-seconds:86400}") long completedTtlSeconds) {
        this.redisTemplate = redisTemplate;
        this.enabled = enabled;
        this.lockTtl = Duration.ofSeconds(Math.max(5, lockTtlSeconds));
        this.completedTtl = Duration.ofSeconds(Math.max(60, completedTtlSeconds));
    }

    public boolean tryAcquire(String idempotencyKey, String ownerToken) {
        if (!enabled || idempotencyKey == null) {
            return true;
        }
        try {
            String key = redisKey(idempotencyKey);
            return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key, "IN_FLIGHT:" + ownerToken, lockTtl));
        } catch (DataAccessException ex) {
            log.warn("Global idempotency lock unavailable key={} reason={}", idempotencyKey, ex.toString());
            // Fail open if global idempotency store is unavailable.
            return true;
        }
    }

    public Optional<UUID> resolveCompletedOrderId(String idempotencyKey) {
        if (!enabled || idempotencyKey == null) {
            return Optional.empty();
        }
        try {
            String value = redisTemplate.opsForValue().get(redisKey(idempotencyKey));
            if (value == null || value.startsWith("IN_FLIGHT:")) {
                return Optional.empty();
            }
            return Optional.of(UUID.fromString(value));
        } catch (Exception ex) {
            log.warn("Global idempotency resolve failed key={} reason={}", idempotencyKey, ex.toString());
            return Optional.empty();
        }
    }

    public void markCompleted(String idempotencyKey, UUID orderId) {
        if (!enabled || idempotencyKey == null || orderId == null) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(redisKey(idempotencyKey), orderId.toString(), completedTtl);
        } catch (DataAccessException ex) {
            log.warn("Global idempotency completion write failed key={} orderId={} reason={}",
                    idempotencyKey, orderId, ex.toString());
        }
    }

    private String redisKey(String idempotencyKey) {
        return "global:idempotency:" + idempotencyKey;
    }
}
