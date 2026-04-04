package com.example.orderprocessing.application.port;

import java.util.Optional;
import java.time.Duration;

/**
 * CacheProvider interface defines a stable boundary used by collaborating components.
 * It is used to keep the boots the Spring runtime for the service layer explicit and maintainable in this architecture.
 */
public interface CacheProvider {
    /**
     * Performs get.
     * @param key input argument used by this operation
     * @param type input argument used by this operation
     * @return operation result
     */
    <T> Optional<T> get(String key, Class<T> type);
    /**
     * Performs put.
     * @param key input argument used by this operation
     * @param value input argument used by this operation
     */
    void put(String key, Object value);
    /**
     * Performs put.
     * @param key input argument used by this operation
     * @param value input argument used by this operation
     * @param ttl input argument used by this operation
     */
    void put(String key, Object value, Duration ttl);
    /**
     * Performs evict.
     * @param key input argument used by this operation
     */
    void evict(String key);
}
