package com.example.orderprocessing.infrastructure.resilience;

import com.example.orderprocessing.application.port.OutboxRepository;
import com.example.orderprocessing.infrastructure.persistence.entity.OutboxStatus;
import com.zaxxer.hikari.HikariDataSource;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.AtomicLong;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BackpressureManager {

    public enum Level {
        NORMAL,
        ELEVATED,
        CRITICAL
    }

    private static final Logger log = LoggerFactory.getLogger(BackpressureManager.class);

    private final OutboxRepository outboxRepository;
    private final DataSource dataSource;
    private final long elevatedOutboxBacklog;
    private final long criticalOutboxBacklog;
    private final long elevatedKafkaLagMs;
    private final long criticalKafkaLagMs;
    private final double elevatedDbUtilization;
    private final double criticalDbUtilization;

    private final AtomicLong lastKafkaLagMs = new AtomicLong(0);
    private final AtomicLong outboxBacklogGauge = new AtomicLong(0);
    private final AtomicLong dbSaturationPercentGauge = new AtomicLong(0);
    private final AtomicLong levelOrdinalGauge = new AtomicLong(Level.NORMAL.ordinal());

    private volatile Level currentLevel = Level.NORMAL;

    public BackpressureManager(
            OutboxRepository outboxRepository,
            DataSource dataSource,
            MeterRegistry meterRegistry,
            @Value("${app.backpressure.outbox.elevated-backlog:2000}") long elevatedOutboxBacklog,
            @Value("${app.backpressure.outbox.critical-backlog:10000}") long criticalOutboxBacklog,
            @Value("${app.backpressure.kafka.elevated-lag-ms:120000}") long elevatedKafkaLagMs,
            @Value("${app.backpressure.kafka.critical-lag-ms:600000}") long criticalKafkaLagMs,
            @Value("${app.backpressure.db.elevated-utilization:0.75}") double elevatedDbUtilization,
            @Value("${app.backpressure.db.critical-utilization:0.9}") double criticalDbUtilization) {
        this.outboxRepository = outboxRepository;
        this.dataSource = dataSource;
        this.elevatedOutboxBacklog = Math.max(100, elevatedOutboxBacklog);
        this.criticalOutboxBacklog = Math.max(this.elevatedOutboxBacklog, criticalOutboxBacklog);
        this.elevatedKafkaLagMs = Math.max(0, elevatedKafkaLagMs);
        this.criticalKafkaLagMs = Math.max(this.elevatedKafkaLagMs, criticalKafkaLagMs);
        this.elevatedDbUtilization = clamp01(elevatedDbUtilization);
        this.criticalDbUtilization = Math.max(this.elevatedDbUtilization, clamp01(criticalDbUtilization));
        Gauge.builder("backpressure.outbox.backlog", outboxBacklogGauge, AtomicLong::get).register(meterRegistry);
        Gauge.builder("backpressure.kafka.lag.ms", lastKafkaLagMs, AtomicLong::get).register(meterRegistry);
        Gauge.builder("backpressure.db.saturation.percent", dbSaturationPercentGauge, AtomicLong::get).register(meterRegistry);
        Gauge.builder("backpressure.level", levelOrdinalGauge, AtomicLong::get).register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${app.backpressure.poll-ms:5000}")
    public void refresh() {
        long pending = outboxRepository.countByStatus(OutboxStatus.PENDING);
        long failed = outboxRepository.countByStatus(OutboxStatus.FAILED);
        long backlog = pending + failed;
        double dbUtilization = resolveDbUtilization();
        Level previous = currentLevel;
        currentLevel = classify(backlog, lastKafkaLagMs.get(), dbUtilization);
        outboxBacklogGauge.set(backlog);
        dbSaturationPercentGauge.set(Math.round(dbUtilization * 100.0));
        levelOrdinalGauge.set(currentLevel.ordinal());
        if (previous != currentLevel) {
            log.warn("Backpressure level changed from={} to={} outboxBacklog={} kafkaLagMs={} dbUtilization={}",
                    previous, currentLevel, backlog, lastKafkaLagMs.get(), dbUtilization);
        }
    }

    public void recordKafkaLagMs(long lagMs) {
        lastKafkaLagMs.set(Math.max(0, lagMs));
    }

    public Level level() {
        return currentLevel;
    }

    public boolean shouldRejectWrites() {
        return currentLevel == Level.CRITICAL;
    }

    public double throttlingFactor() {
        if (currentLevel == Level.CRITICAL) {
            return 0.35D;
        }
        if (currentLevel == Level.ELEVATED) {
            return 0.70D;
        }
        return 1.0D;
    }

    private Level classify(long outboxBacklog, long kafkaLagMs, double dbUtilization) {
        if (outboxBacklog >= criticalOutboxBacklog
                || kafkaLagMs >= criticalKafkaLagMs
                || dbUtilization >= criticalDbUtilization) {
            return Level.CRITICAL;
        }
        if (outboxBacklog >= elevatedOutboxBacklog
                || kafkaLagMs >= elevatedKafkaLagMs
                || dbUtilization >= elevatedDbUtilization) {
            return Level.ELEVATED;
        }
        return Level.NORMAL;
    }

    private double resolveDbUtilization() {
        try {
            if (dataSource instanceof HikariDataSource hikari) {
                int total = hikari.getHikariPoolMXBean().getTotalConnections();
                int active = hikari.getHikariPoolMXBean().getActiveConnections();
                if (total <= 0) {
                    return 0.0D;
                }
                return Math.min(1.0D, Math.max(0.0D, ((double) active) / (double) total));
            }
        } catch (Exception ex) {
            log.debug("Unable to resolve DB utilization reason={}", ex.toString());
        }
        return 0.0D;
    }

    private double clamp01(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }
}
