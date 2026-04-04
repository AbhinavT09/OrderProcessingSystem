package com.example.orderprocessing.application.port;

import java.util.Optional;
import java.time.Duration;

/**
 * Application port for cache operations used by read-side services.
 *
 * <p>Adapters should prefer fail-soft behavior so cache outages do not break request handling.</p>
 */
public interface CacheProvider {
    /**
     * Reads a typed cache value.
     *
     * @param key cache key
     * @param type expected value type
     * @return cached value when available
     */
    <T> Optional<T> get(String key, Class<T> type);
    /**
     * Writes a value with adapter-default TTL behavior.
     *
     * @param key cache key
     * @param value serializable value
     */
    void put(String key, Object value);
    /**
     * Writes a value with explicit TTL.
     *
     * @param key cache key
     * @param value serializable value
     * @param ttl requested TTL
     */
    void put(String key, Object value, Duration ttl);
    /**
     * Removes a cache entry.
     *
     * @param key cache key
     */
    void evict(String key);
}
