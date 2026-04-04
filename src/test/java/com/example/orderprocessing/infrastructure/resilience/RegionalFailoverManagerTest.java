package com.example.orderprocessing.infrastructure.resilience;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.sql.Connection;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RegionalFailoverManagerTest {

    @Test
    void switchesToPassiveWhenDependenciesUnhealthy() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection conn = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(conn);
        when(conn.isValid(1)).thenReturn(false);

        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        RedisConnectionFactory redisFactory = mock(RedisConnectionFactory.class);
        RedisConnection redisConnection = mock(RedisConnection.class);
        when(redisTemplate.getConnectionFactory()).thenReturn(redisFactory);
        when(redisFactory.getConnection()).thenReturn(redisConnection);
        when(redisConnection.ping()).thenReturn(null);

        RegionalFailoverManager manager = new RegionalFailoverManager(
                dataSource,
                redisTemplate,
                new SimpleMeterRegistry(),
                true,
                true,
                1,
                1,
                2000,
                150,
                1,
                1,
                1,
                "region-a",
                "active-passive",
                "localhost:9092");

        manager.monitorAndFailover();
        assertFalse(manager.allowsWrites());
    }

    @Test
    void remainsWritableWhenMultiRegionDisabled() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection conn = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(conn);
        when(conn.isValid(1)).thenReturn(false);

        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        RedisConnectionFactory redisFactory = mock(RedisConnectionFactory.class);
        RedisConnection redisConnection = mock(RedisConnection.class);
        when(redisTemplate.getConnectionFactory()).thenReturn(redisFactory);
        when(redisFactory.getConnection()).thenReturn(redisConnection);
        when(redisConnection.ping()).thenReturn(null);

        RegionalFailoverManager manager = new RegionalFailoverManager(
                dataSource,
                redisTemplate,
                new SimpleMeterRegistry(),
                false,
                true,
                1,
                1,
                2000,
                150,
                1,
                1,
                1,
                "region-a",
                "active-passive",
                "localhost:9092");

        manager.monitorAndFailover();
        assertTrue(manager.allowsWrites());
    }
}
