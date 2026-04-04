package com.example.orderprocessing.application.port;

import java.util.Optional;

public interface CacheProvider {
    <T> Optional<T> get(String key, Class<T> type);
    void put(String key, Object value);
    void evict(String key);
}
