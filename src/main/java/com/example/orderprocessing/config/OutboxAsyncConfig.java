package com.example.orderprocessing.config;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Dedicated async infrastructure for outbox completion paths.
 *
 * <p>Separates Kafka producer I/O threads from blocking database work so a saturated connection
 * pool cannot stall all producer callbacks.</p>
 */
@Configuration
public class OutboxAsyncConfig {

    /**
     * Worker pool for post-publish outbox state transitions (success and retry paths).
     *
     * @param poolSize core and max pool size
     * @return executor bound by {@code app.outbox.publisher.db-update-pool-size}
     */
    @Bean(name = "outboxDbUpdateExecutor")
    public ThreadPoolTaskExecutor outboxDbUpdateExecutor(
            @Value("${app.outbox.publisher.db-update-pool-size:8}") int poolSize) {
        int size = Math.max(2, poolSize);
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("outbox-db-update-");
        executor.setCorePoolSize(size);
        executor.setMaxPoolSize(size);
        executor.setQueueCapacity(1024);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
