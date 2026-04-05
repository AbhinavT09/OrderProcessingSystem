package com.example.orderprocessing.infrastructure.messaging;

import com.example.orderprocessing.application.port.OutboxRepository;
import com.example.orderprocessing.infrastructure.persistence.entity.OutboxEntity;
import com.example.orderprocessing.infrastructure.persistence.entity.OutboxStatus;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
/**
 * Infrastructure scheduler for transactional outbox delivery.
 *
 * <p>Coordinates partition-aware polling, bounded parallel processing, and cleanup of sent events.
 * The component provides backpressure through an in-flight semaphore and bounded batch size to
 * avoid overwhelming downstream Kafka infrastructure.</p>
 *
 * <p>Duplicate publication risk is reduced by lease/claim semantics in the fetcher and by explicit
 * status transitions handled by processor/retry collaborators.</p>
 *
 * <p><b>Resilience context:</b> combines worker-level backpressure, partition ownership sharding,
 * and completion-aware async handling to avoid over-dispatch during broker slowness.</p>
 *
 * <p><b>Transactional context:</b> scheduling is non-transactional, but cleanup and repository
 * transitions execute through DB transaction templates.</p>
 */
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxRepository outboxRepository;
    private final OutboxFetcher outboxFetcher;
    private final OutboxProcessor outboxProcessor;
    private final TransactionTemplate transactionTemplate;

    private final int maxRetries;
    private final int batchSize;
    private final int totalPartitions;
    private final int instanceId;
    private final int instanceCount;
    private final long slowKafkaThresholdMs;
    private final int cleanupRetentionDays;

    private final ExecutorService partitionExecutor;
    private final Semaphore inFlightSemaphore;

    private final AtomicLong pendingGaugeValue = new AtomicLong(0);
    private final AtomicLong failureGaugeValue = new AtomicLong(0);
    private final AtomicInteger activeWorkers = new AtomicInteger(0);

    /**
     * Creates the scheduled outbox publication coordinator.
     * @param outboxFetcher outbox fetch/claim component
     * @param outboxProcessor event publication component
     * @param outboxRetryHandler retry/backoff component
     * @param outboxRepository repository for cleanup operations
     * @param meterRegistry metrics registry
     * @param taskExecutor worker executor for partition processing
     */
    public OutboxPublisher(
            OutboxRepository outboxRepository,
            OutboxFetcher outboxFetcher,
            OutboxProcessor outboxProcessor,
            TransactionTemplate transactionTemplate,
            MeterRegistry meterRegistry,
            @Value("${app.outbox.max-retries:12}") int maxRetries,
            @Value("${app.outbox.publisher.batch-size:200}") int batchSize,
            @Value("${app.outbox.publisher.parallelism:8}") int parallelism,
            @Value("${app.outbox.publisher.max-in-flight:16}") int maxInFlight,
            @Value("${app.outbox.partition.total:64}") int totalPartitions,
            @Value("${app.outbox.partition.instance-id:0}") int instanceId,
            @Value("${app.outbox.partition.instance-count:1}") int instanceCount,
            @Value("${app.outbox.publisher.slow-kafka-threshold-ms:1500}") long slowKafkaThresholdMs,
            @Value("${app.outbox.cleanup.retention-days:7}") int cleanupRetentionDays) {
        this.outboxRepository = outboxRepository;
        this.outboxFetcher = outboxFetcher;
        this.outboxProcessor = outboxProcessor;
        this.transactionTemplate = transactionTemplate;
        this.maxRetries = maxRetries;
        this.batchSize = Math.max(100, Math.min(batchSize, 500));
        this.totalPartitions = Math.max(1, totalPartitions);
        this.instanceId = Math.max(0, instanceId);
        this.instanceCount = Math.max(1, instanceCount);
        this.slowKafkaThresholdMs = Math.max(100, slowKafkaThresholdMs);
        this.cleanupRetentionDays = Math.max(1, cleanupRetentionDays);

        this.partitionExecutor = Executors.newFixedThreadPool(Math.max(1, parallelism));
        this.inFlightSemaphore = new Semaphore(Math.max(1, maxInFlight));
        Gauge.builder("outbox.pending.count", pendingGaugeValue, AtomicLong::get).register(meterRegistry);
        Gauge.builder("outbox.failure.count", failureGaugeValue, AtomicLong::get).register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${app.outbox.publisher.poll-ms:2000}")
    /**
     * Runs one scheduled publish cycle for all partitions owned by this instance.
     *
     * <p>Behavioral guarantees:</p>
     * <ul>
     *   <li>Applies backpressure by skipping when worker capacity is saturated.</li>
     *   <li>Bounds work with per-partition batches to keep latency predictable.</li>
     *   <li>Delegates retry and terminal-failure transitions to outbox processing pipeline.</li>
     * </ul>
     */
    public void pollAndPublish() {
        refreshGauges();

        // Backpressure: skip cycle if inflight workers are saturated.
        if (inFlightSemaphore.availablePermits() == 0) {
            return;
        }

        List<Integer> ownedPartitions = ownedPartitions();
        for (Integer partition : ownedPartitions) {
            if (!inFlightSemaphore.tryAcquire()) {
                break;
            }
            activeWorkers.incrementAndGet();
            partitionExecutor.submit(() -> {
                try {
                    processPartitionBatch(partition);
                } catch (RuntimeException ex) {
                    log.error("Outbox partition worker failed partition={} reason={}", partition, ex.toString());
                } finally {
                    activeWorkers.decrementAndGet();
                    inFlightSemaphore.release();
                }
            });
        }
        refreshGauges();
    }

    @Scheduled(fixedDelayString = "${app.outbox.cleanup.poll-ms:60000}")
    /**
     * Archives and purges old sent outbox rows.
     *
     * <p>This keeps the active outbox table small for faster claim scans while preserving
     * historical observability in archive storage.</p>
     */
    public void cleanupSentEvents() {
        Instant cutoff = Instant.now().minus(cleanupRetentionDays, ChronoUnit.DAYS);
        transactionTemplate.executeWithoutResult(status -> {
            List<OutboxEntity> toArchive = outboxRepository.findSentOlderThan(cutoff);
            if (toArchive.isEmpty()) {
                return;
            }
            outboxRepository.saveArchiveBatch(toArchive, Instant.now());
            outboxRepository.deleteBatch(toArchive);
            log.info("Outbox cleanup archived={} cutoff={}", toArchive.size(), cutoff);
        });
    }

    private void processPartitionBatch(int partition) {
        List<OutboxEntity> claimed = outboxFetcher.claimPartitionBatch(partition, maxRetries, batchSize);
        if (claimed.isEmpty()) {
            return;
        }
        try {
            outboxProcessor.processBatch(claimed).join();
        } catch (CompletionException ex) {
            Throwable cause = ex.getCause() == null ? ex : ex.getCause();
            log.warn("Outbox async publish completion had failures partition={} reason={}", partition, cause.toString());
        }
    }

    private List<Integer> ownedPartitions() {
        List<Integer> partitions = new ArrayList<>();
        for (int p = 0; p < totalPartitions; p++) {
            if (p % instanceCount == instanceId) {
                partitions.add(p);
            }
        }
        return partitions;
    }

    private void refreshGauges() {
        pendingGaugeValue.set(outboxRepository.countByStatus(OutboxStatus.PENDING));
        failureGaugeValue.set(outboxRepository.countByStatus(OutboxStatus.FAILED));
    }

    @PreDestroy
    /**
     * Gracefully stops background workers during container shutdown.
     */
    public void shutdown() {
        partitionExecutor.shutdown();
        try {
            if (!partitionExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                partitionExecutor.shutdownNow();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            partitionExecutor.shutdownNow();
        }
    }
}
