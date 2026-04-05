package com.example.orderprocessing.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
/**
 * Central transaction utility wiring for infrastructure components.
 *
 * <p>Several collaborators use {@link TransactionTemplate} directly for explicit transactional
 * boundaries around retry, outbox processing, and consumer dedupe paths.</p>
 */
public class TransactionConfig {

    /**
     * Exposes a shared transaction template backed by the primary transaction manager.
     *
     * @param transactionManager active platform transaction manager
     * @return transaction template used by infrastructure services
     */
    @Bean
    public TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }
}
