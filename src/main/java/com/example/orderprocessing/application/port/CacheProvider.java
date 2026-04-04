package com.example.orderprocessing.application.port;

import java.util.Optional;
import java.time.Duration;

public interface CacheProvider {
    <T> Optional<T> get(String key, Class<T> type);
    void put(String key, Object value);
    void put(String key, Object value, Duration ttl);
    void evict(String key);
}
