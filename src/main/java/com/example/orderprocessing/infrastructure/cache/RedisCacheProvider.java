package com.example.orderprocessing.infrastructure.cache;

import com.example.orderprocessing.application.port.CacheProvider;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class RedisCacheProvider implements CacheProvider {

    private static final Logger log = LoggerFactory.getLogger(RedisCacheProvider.class);

    private final Map<String, Object> cache = new ConcurrentHashMap<>();

    @Override
    public <T> Optional<T> get(String key, Class<T> type) {
        try {
            Object value = cache.get(key);
            if (value == null || !type.isInstance(value)) {
                return Optional.empty();
            }
            return Optional.of(type.cast(value));
        } catch (RuntimeException ex) {
            log.warn("Redis cache read failure key={} reason={}", key, ex.toString());
            return Optional.empty();
        }
    }

    @Override
    public void put(String key, Object value) {
        try {
            cache.put(key, value);
        } catch (RuntimeException ex) {
            log.warn("Redis cache write failure key={} reason={}", key, ex.toString());
        }
    }

    @Override
    public void evict(String key) {
        try {
            cache.remove(key);
        } catch (RuntimeException ex) {
            log.warn("Redis cache eviction failure key={} reason={}", key, ex.toString());
        }
    }
}
