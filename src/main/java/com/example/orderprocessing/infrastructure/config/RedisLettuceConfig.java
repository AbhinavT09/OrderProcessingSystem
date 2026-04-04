package com.example.orderprocessing.infrastructure.config;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.TimeoutOptions;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.data.redis.LettuceClientConfigurationBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedisLettuceConfig {

    @Bean
    LettuceClientConfigurationBuilderCustomizer lettuceClientConfigurationBuilderCustomizer(
            @Value("${spring.data.redis.timeout:2s}") Duration commandTimeout,
            @Value("${spring.data.redis.connect-timeout:2s}") Duration connectTimeout) {
        return builder -> builder
                .commandTimeout(commandTimeout)
                .shutdownTimeout(Duration.ofMillis(100))
                .clientOptions(ClientOptions.builder()
                        .autoReconnect(true)
                        .socketOptions(SocketOptions.builder()
                                .connectTimeout(connectTimeout)
                                .keepAlive(true)
                                .build())
                        .timeoutOptions(TimeoutOptions.enabled(commandTimeout))
                        .build());
    }
}
