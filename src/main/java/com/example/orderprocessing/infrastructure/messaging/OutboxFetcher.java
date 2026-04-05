package com.example.orderprocessing.infrastructure.messaging;

import com.example.orderprocessing.application.port.OutboxRepository;
import com.example.orderprocessing.infrastructure.persistence.entity.OutboxEntity;
import com.example.orderprocessing.infrastructure.persistence.entity.OutboxStatus;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
/**
 * Claims partition-scoped outbox batches and assigns short in-flight leases.
 *
 * <p>Leases are fenced with an owner token so later status transitions can verify claim ownership
 * and avoid stale workers overwriting newer retries.</p>
 *
 * <p><b>Architecture role:</b> infrastructure adapter in the outbox publication pipeline.</p>
 *
 * <p><b>Transactional context:</b> claim and lease assignment execute in one DB transaction,
 * guaranteeing atomic transition to {@code IN_FLIGHT} before worker release.</p>
 */
public class OutboxFetcher {

    private final OutboxRepository outboxRepository;
    private final TransactionTemplate transactionTemplate;
    private final DistributionSummary batchSizeSummary;
    private final long leaseMs;

    /**
     * Creates a partition-aware outbox batch fetcher.
     * @param outboxRepository outbox persistence port
     * @param clock clock used for claim timestamp
     */
    public OutboxFetcher(OutboxRepository outboxRepository,
                         TransactionTemplate transactionTemplate,
                         MeterRegistry meterRegistry,
                         @Value("${app.outbox.publisher.in-flight-lease-ms:30000}") long leaseMs) {
        this.outboxRepository = outboxRepository;
        this.transactionTemplate = transactionTemplate;
        this.batchSizeSummary = DistributionSummary.builder("outbox.batch.size").register(meterRegistry);
        this.leaseMs = Math.max(1000, leaseMs);
    }

    /**
     * Claims due rows for one partition and writes lease metadata.
     *
     * @param partition owned partition index
     * @param maxRetries retry limit for eligibility
     * @param batchSize maximum rows to claim
     * @return leased rows ordered deterministically by aggregate and creation time
     */
    public List<OutboxEntity> claimPartitionBatch(int partition, int maxRetries, int batchSize) {
        return transactionTemplate.execute(status -> {
            Instant now = Instant.now();
            List<OutboxEntity> claimed = outboxRepository.claimBatchForPartition(partition, now, maxRetries, batchSize);
            if (claimed == null || claimed.isEmpty()) {
                return List.of();
            }
            Instant leaseUntil = now.plusMillis(leaseMs);
            String leaseOwner = "publisher-" + partition + "-" + UUID.randomUUID();
            for (OutboxEntity event : claimed) {
                event.setStatus(OutboxStatus.IN_FLIGHT);
                event.setNextAttemptAt(leaseUntil);
                event.setLeaseOwner(leaseOwner);
                long version = event.getLeaseVersion() == null ? 0L : event.getLeaseVersion();
                event.setLeaseVersion(version + 1L);
            }
            outboxRepository.saveAll(claimed);
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
