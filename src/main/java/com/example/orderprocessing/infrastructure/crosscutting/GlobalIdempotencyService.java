package com.example.orderprocessing.infrastructure.crosscutting;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
/**
 * Infrastructure-layer coordinator for cross-node idempotency lifecycle state.
 *
 * <p>Stores request state in Redis using lifecycle semantics:</p>
 * <ul>
 *   <li><b>IN_PROGRESS</b> while business write is executing.</li>
 *   <li><b>COMPLETED</b> once commit succeeds and order id is known.</li>
 * </ul>
 *
 * <p>The service is backward compatible with legacy key formats and intentionally fails open
 * when Redis is unavailable to preserve write-path availability.</p>
 */
public class GlobalIdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(GlobalIdempotencyService.class);
    private static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String VALUE_PREFIX = "V2|";

    private final StringRedisTemplate redisTemplate;
    private final Duration lockTtl;
    private final Duration completedTtl;
    private final boolean enabled;

    /**
     * Creates the global idempotency coordinator.
     * @param redisTemplate redis template for lock and mapping keys
     * @param lockTtl lock TTL duration
     * @param completedTtl completed mapping TTL duration
     */
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

    /**
     * Logical lifecycle status of an idempotent request key.
     */
    public enum IdempotencyStatus {
        ABSENT,
        IN_PROGRESS,
        COMPLETED
    }

    /**
     * Immutable view of the resolved idempotency state from Redis.
     */
    public record IdempotencyState(IdempotencyStatus status, UUID orderId, Instant timestamp) {
        /**
         * Indicates whether the key can be deterministically mapped to a completed order.
         */
        public boolean hasCompletedOrder() {
            return status == IdempotencyStatus.COMPLETED && orderId != null;
        }
    }

    /**
     * Reads idempotency lifecycle state for a request key.
     *
     * <p>Backward compatibility rules:</p>
     * <ul>
     *   <li>raw UUID value -> interpreted as COMPLETED</li>
     *   <li>{@code IN_FLIGHT:*} legacy value -> interpreted as IN_PROGRESS</li>
     * </ul>
     *
     * @param idempotencyKey external request key
     * @return resolved state; ABSENT when key is missing or unavailable
     */
    public IdempotencyState resolveState(String idempotencyKey) {
        if (!enabled || idempotencyKey == null) {
            return new IdempotencyState(IdempotencyStatus.ABSENT, null, null);
        }
        try {
            String value = redisTemplate.opsForValue().get(redisKey(idempotencyKey));
            return parseValue(value);
        } catch (Exception ex) {
            log.warn("Global idempotency resolve failed key={} reason={}", idempotencyKey, ex.toString());
            return new IdempotencyState(IdempotencyStatus.ABSENT, null, null);
        }
    }

    /**
     * Attempts to reserve a key in IN_PROGRESS state.
     *
     * <p>Uses atomic set-if-absent to prevent concurrent duplicate processing.</p>
     *
     * @param idempotencyKey request key
     * @return {@code true} when reservation succeeds (or feature disabled), otherwise {@code false}
     */
    public boolean markInProgress(String idempotencyKey) {
        if (!enabled || idempotencyKey == null) {
            return true;
        }
        try {
            String key = redisKey(idempotencyKey);
            String value = serialize(STATUS_IN_PROGRESS, null, Instant.now());
            return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key, value, lockTtl));
        } catch (DataAccessException ex) {
            log.warn("Global idempotency in-progress write unavailable key={} reason={}", idempotencyKey, ex.toString());
            // Fail open if global idempotency store is unavailable.
            return true;
        }
    }

    /**
     * Legacy compatibility alias for reserving IN_PROGRESS.
     *
     * @param idempotencyKey request key
     * @param ownerToken ignored token retained for signature compatibility
     * @return result of IN_PROGRESS reservation
     */
    public boolean tryAcquire(String idempotencyKey, String ownerToken) {
        return markInProgress(idempotencyKey);
    }

    /**
     * Resolves completed order id when key state is COMPLETED.
     *
     * @param idempotencyKey request key
     * @return order id when available; empty for ABSENT/IN_PROGRESS/non-resolvable values
     */
    public Optional<UUID> resolveCompletedOrderId(String idempotencyKey) {
        IdempotencyState state = resolveState(idempotencyKey);
        return state.hasCompletedOrder() ? Optional.of(state.orderId()) : Optional.empty();
    }

    /**
     * Transitions key state to COMPLETED with durable order id mapping.
     *
     * <p>Expected to be invoked after DB commit to avoid false completion on partial failures.</p>
     *
     * @param idempotencyKey request key
     * @param orderId committed order id associated with the request
     */
    public void markCompleted(String idempotencyKey, UUID orderId) {
        if (!enabled || idempotencyKey == null || orderId == null) {
            return;
        }
        try {
            String value = serialize(STATUS_COMPLETED, orderId, Instant.now());
            redisTemplate.opsForValue().set(redisKey(idempotencyKey), value, completedTtl);
        } catch (DataAccessException ex) {
            log.warn("Global idempotency completion write failed key={} orderId={} reason={}",
                    idempotencyKey, orderId, ex.toString());
        }
    }

    private IdempotencyState parseValue(String value) {
        if (value == null || value.isBlank()) {
            return new IdempotencyState(IdempotencyStatus.ABSENT, null, null);
        }

        // Backward compatibility: legacy in-flight marker.
        if (value.startsWith("IN_FLIGHT:")) {
            return new IdempotencyState(IdempotencyStatus.IN_PROGRESS, null, null);
        }

        // Backward compatibility: legacy raw UUID value means completed.
        try {
            return new IdempotencyState(IdempotencyStatus.COMPLETED, UUID.fromString(value), null);
        } catch (IllegalArgumentException ignored) {
            // Continue to parse V2 format below.
        }

        if (!value.startsWith(VALUE_PREFIX)) {
            return new IdempotencyState(IdempotencyStatus.ABSENT, null, null);
        }

        String[] parts = value.split("\\|", 4);
        if (parts.length < 4) {
            return new IdempotencyState(IdempotencyStatus.ABSENT, null, null);
        }
        String status = parts[1];
        String orderIdPart = parts[2];
        String timestampPart = parts[3];

        Instant timestamp = null;
        if (!timestampPart.isBlank()) {
            try {
                timestamp = Instant.ofEpochMilli(Long.parseLong(timestampPart));
            } catch (NumberFormatException ignored) {
                timestamp = null;
            }
        }

        if (STATUS_IN_PROGRESS.equals(status)) {
            return new IdempotencyState(IdempotencyStatus.IN_PROGRESS, null, timestamp);
        }
        if (STATUS_COMPLETED.equals(status)) {
            try {
                UUID orderId = orderIdPart.isBlank() ? null : UUID.fromString(orderIdPart);
                return new IdempotencyState(IdempotencyStatus.COMPLETED, orderId, timestamp);
            } catch (IllegalArgumentException ignored) {
                return new IdempotencyState(IdempotencyStatus.COMPLETED, null, timestamp);
            }
        }
        return new IdempotencyState(IdempotencyStatus.ABSENT, null, timestamp);
    }

    private String serialize(String status, UUID orderId, Instant timestamp) {
        String orderPart = orderId == null ? "" : orderId.toString();
        long epochMillis = timestamp == null ? Instant.now().toEpochMilli() : timestamp.toEpochMilli();
        return VALUE_PREFIX + status + "|" + orderPart + "|" + epochMillis;
    }

    private String redisKey(String idempotencyKey) {
        return "global:idempotency:" + idempotencyKey;
    }
}
