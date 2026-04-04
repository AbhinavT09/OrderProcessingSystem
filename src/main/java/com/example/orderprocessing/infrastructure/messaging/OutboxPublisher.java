package com.example.orderprocessing.infrastructure.messaging;

import com.example.orderprocessing.application.event.OrderCreatedEvent;
import com.example.orderprocessing.application.port.EventPublisher;
import com.example.orderprocessing.application.port.OutboxRepository;
import com.example.orderprocessing.infrastructure.messaging.schema.OrderCreatedEventSchemaRegistry;
import com.example.orderprocessing.infrastructure.persistence.entity.OutboxEntity;
import com.example.orderprocessing.infrastructure.persistence.entity.OutboxStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxRepository outboxRepository;
    private final EventPublisher eventPublisher;
    private final OrderCreatedEventSchemaRegistry schemaRegistry;
    private final TransactionTemplate transactionTemplate;

    private final int maxRetries;
    private final long backoffBaseMs;
    private final int batchSize;
    private final int totalPartitions;
    private final int instanceId;
    private final int instanceCount;
    private final long slowKafkaThresholdMs;
    private final int cleanupRetentionDays;

    private final ExecutorService partitionExecutor;
    private final Semaphore inFlightSemaphore;
    private final AtomicLong lastBatchDurationMs = new AtomicLong(0);

    private final Timer publishLatencyTimer;
    private final DistributionSummary batchSizeSummary;
    private final DistributionSummary outboxLagSummary;
    private final Counter publishRateCounter;
    private final Counter retryCounter;
    private final AtomicLong pendingGaugeValue = new AtomicLong(0);
    private final AtomicLong failureGaugeValue = new AtomicLong(0);
    private final AtomicInteger activeWorkers = new AtomicInteger(0);

    public OutboxPublisher(
            OutboxRepository outboxRepository,
            EventPublisher eventPublisher,
            OrderCreatedEventSchemaRegistry schemaRegistry,
            TransactionTemplate transactionTemplate,
            MeterRegistry meterRegistry,
            @Value("${app.outbox.max-retries:12}") int maxRetries,
            @Value("${app.outbox.backoff-base-ms:1000}") long backoffBaseMs,
            @Value("${app.outbox.publisher.batch-size:200}") int batchSize,
            @Value("${app.outbox.publisher.parallelism:8}") int parallelism,
            @Value("${app.outbox.publisher.max-in-flight:16}") int maxInFlight,
            @Value("${app.outbox.partition.total:64}") int totalPartitions,
            @Value("${app.outbox.partition.instance-id:0}") int instanceId,
            @Value("${app.outbox.partition.instance-count:1}") int instanceCount,
            @Value("${app.outbox.publisher.slow-kafka-threshold-ms:1500}") long slowKafkaThresholdMs,
            @Value("${app.outbox.cleanup.retention-days:7}") int cleanupRetentionDays) {
        this.outboxRepository = outboxRepository;
        this.eventPublisher = eventPublisher;
        this.schemaRegistry = schemaRegistry;
        this.transactionTemplate = transactionTemplate;
        this.maxRetries = maxRetries;
        this.backoffBaseMs = backoffBaseMs;
        this.batchSize = Math.max(100, Math.min(batchSize, 500));
        this.totalPartitions = Math.max(1, totalPartitions);
        this.instanceId = Math.max(0, instanceId);
        this.instanceCount = Math.max(1, instanceCount);
        this.slowKafkaThresholdMs = Math.max(100, slowKafkaThresholdMs);
        this.cleanupRetentionDays = Math.max(1, cleanupRetentionDays);

        this.partitionExecutor = Executors.newFixedThreadPool(Math.max(1, parallelism));
        this.inFlightSemaphore = new Semaphore(Math.max(1, maxInFlight));

        this.publishLatencyTimer = meterRegistry.timer("outbox.publish.latency");
        this.batchSizeSummary = DistributionSummary.builder("outbox.batch.size").register(meterRegistry);
        this.outboxLagSummary = DistributionSummary.builder("outbox.lag").register(meterRegistry);
        this.publishRateCounter = meterRegistry.counter("outbox.publish.rate");
        this.retryCounter = meterRegistry.counter("outbox.retry.count");
        Gauge.builder("outbox.pending.count", pendingGaugeValue, AtomicLong::get).register(meterRegistry);
        Gauge.builder("outbox.failure.count", failureGaugeValue, AtomicLong::get).register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${app.outbox.publisher.poll-ms:2000}")
    public void pollAndPublish() {
        refreshGauges();

        // Dynamic backpressure: if recent batch was slow or inflight is saturated, skip cycle.
        if (lastBatchDurationMs.get() > slowKafkaThresholdMs || inFlightSemaphore.availablePermits() == 0) {
            return;
        }

        List<Integer> ownedPartitions = ownedPartitions();
        for (Integer partition : ownedPartitions) {
            if (!inFlightSemaphore.tryAcquire()) {
                break;
            }
            activeWorkers.incrementAndGet();
            partitionExecutor.submit(() -> {
                long start = System.nanoTime();
                try {
                    processPartitionBatch(partition);
                } catch (RuntimeException ex) {
                    log.error("Outbox partition worker failed partition={} reason={}", partition, ex.toString());
                } finally {
                    long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
                    lastBatchDurationMs.set(elapsedMs);
                    activeWorkers.decrementAndGet();
                    inFlightSemaphore.release();
                }
            });
        }
        refreshGauges();
    }

    @Scheduled(fixedDelayString = "${app.outbox.cleanup.poll-ms:60000}")
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
        transactionTemplate.executeWithoutResult(status -> {
            Instant now = Instant.now();
            List<OutboxEntity> claimed = outboxRepository.claimBatchForPartition(partition, now, maxRetries, batchSize);
            if (claimed.isEmpty()) {
                return;
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

            for (OutboxEntity event : claimed) {
                publishOne(event);
            }
        });
    }

    private void publishOne(OutboxEntity outboxEvent) {
        if (outboxEvent.getStatus() == OutboxStatus.SENT) {
            return;
        }

        try {
            OrderCreatedEvent event = parse(outboxEvent.getPayload());
            publishLatencyTimer.record(() -> eventPublisher.publishOrderCreated(event));
            publishRateCounter.increment();
            outboxLagSummary.record(Math.max(0, Duration.between(outboxEvent.getCreatedAt(), Instant.now()).toMillis()));
            outboxEvent.setStatus(OutboxStatus.SENT);
            outboxEvent.setNextAttemptAt(Instant.now());
            outboxRepository.save(outboxEvent);
        } catch (RuntimeException ex) {
            int retries = outboxEvent.getRetryCount() == null ? 0 : outboxEvent.getRetryCount();
            retries++;
            outboxEvent.setRetryCount(retries);
            retryCounter.increment();

            if (retries >= maxRetries) {
                outboxEvent.setStatus(OutboxStatus.FAILED);
                outboxEvent.setNextAttemptAt(Instant.now().plus(3650, ChronoUnit.DAYS));
                outboxRepository.save(outboxEvent);
                log.error("Outbox max retries reached outboxId={} aggregateId={} retries={} reason={}",
                        outboxEvent.getId(), outboxEvent.getAggregateId(), retries, ex.toString());
                return;
            }

            long delayMs = computeBackoffDelayMs(retries);
            outboxEvent.setStatus(OutboxStatus.FAILED);
            outboxEvent.setNextAttemptAt(Instant.now().plusMillis(delayMs));
            outboxRepository.save(outboxEvent);
            log.warn("Outbox publish failed outboxId={} aggregateId={} retries={} nextAttemptInMs={} reason={}",
                    outboxEvent.getId(), outboxEvent.getAggregateId(), retries, delayMs, ex.toString());
        }
    }

    private long computeBackoffDelayMs(int retries) {
        long exp = Math.max(0, retries - 1);
        long delay = (long) (backoffBaseMs * Math.pow(2, exp));
        return Math.min(delay, 300_000L);
    }

    private OrderCreatedEvent parse(String payload) {
        return schemaRegistry.deserialize(payload);
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
