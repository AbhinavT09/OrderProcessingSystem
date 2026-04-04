package com.example.orderprocessing.infrastructure.messaging;

import com.example.orderprocessing.application.port.OutboxRepository;
import com.example.orderprocessing.infrastructure.persistence.entity.OutboxEntity;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
/**
 * OutboxFetcher implements a concrete responsibility in the order processing service.
 * It is used to keep the boots the Spring runtime for the service layer explicit and maintainable in this architecture.
 */
public class OutboxFetcher {

    private final OutboxRepository outboxRepository;
    private final TransactionTemplate transactionTemplate;
    private final DistributionSummary batchSizeSummary;

    /**
     * Creates a partition-aware outbox batch fetcher.
     * @param outboxRepository outbox persistence port
     * @param clock clock used for claim timestamp
     */
    public OutboxFetcher(OutboxRepository outboxRepository,
                         TransactionTemplate transactionTemplate,
                         MeterRegistry meterRegistry) {
        this.outboxRepository = outboxRepository;
        this.transactionTemplate = transactionTemplate;
        this.batchSizeSummary = DistributionSummary.builder("outbox.batch.size").register(meterRegistry);
    }

    /**
     * Executes claimPartitionBatch.
     * @param partition input argument used by this operation
     * @param maxRetries input argument used by this operation
     * @param batchSize input argument used by this operation
     * @return operation result
     */
    public List<OutboxEntity> claimPartitionBatch(int partition, int maxRetries, int batchSize) {
        return transactionTemplate.execute(status -> {
            Instant now = Instant.now();
            List<OutboxEntity> claimed = outboxRepository.claimBatchForPartition(partition, now, maxRetries, batchSize);
            if (claimed == null || claimed.isEmpty()) {
                return List.of();
            }
            // Keep aggregate_id ordering deterministic inside each partition batch.
            claimed.sort((a, b) -> {
                int cmp = a.getAggregateId().compareTo(b.getAggregateId());
                if (cmp != 0) {
                    return cmp;
                }
                return a.getCreatedAt().compareTo(b.getCreatedAt());
            });
            batchSizeSummary.record(claimed.size());
            return claimed;
        });
    }
}
