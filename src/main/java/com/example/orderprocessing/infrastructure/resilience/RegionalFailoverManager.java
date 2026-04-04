package com.example.orderprocessing.infrastructure.resilience;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.Connection;
import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import jakarta.annotation.PreDestroy;
import javax.sql.DataSource;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
/**
 * Infrastructure resilience coordinator for multi-region ACTIVE/PASSIVE operation.
 *
 * <p>Monitors DB, Redis, and Kafka health and gates write traffic when the region is degraded.
 * In ACTIVE/PASSIVE mode, sustained dependency failures switch the node to PASSIVE; sustained
 * recovery switches it back to ACTIVE.</p>
 */
public class RegionalFailoverManager {

    private enum NodeState {
        ACTIVE, PASSIVE
    }

    private static final Logger log = LoggerFactory.getLogger(RegionalFailoverManager.class);

    private final DataSource dataSource;
    private final StringRedisTemplate redisTemplate;
    private final MeterRegistry meterRegistry;
    private final boolean enabled;
    private final boolean autoFailoverEnabled;
    private final int globalUnhealthyThreshold;
    private final int healthyThresholdToRecover;
    private final int dbFailureThreshold;
    private final int redisFailureThreshold;
    private final int kafkaFailureThreshold;
    private final long checkTimeoutMs;
    private final long redisLatencyThresholdMs;
    private final String regionId;
    private final String failoverStrategy;
    private final String kafkaBootstrapServers;
    private final AtomicReference<NodeState> nodeState;
    private final AtomicInteger consecutiveUnhealthy = new AtomicInteger(0);
    private final AtomicInteger consecutiveHealthy = new AtomicInteger(0);
    private final AtomicInteger dbFailures = new AtomicInteger(0);
    private final AtomicInteger redisFailures = new AtomicInteger(0);
    private final AtomicInteger kafkaFailures = new AtomicInteger(0);
    private final Counter failoverEventsCounter;
    private final Counter redisSlowChecksCounter;
    private final AdminClient adminClient;

    /**
     * Creates the regional failover manager.
     * @param jdbcTemplate JDBC template used for DB health checks
     * @param redisTemplate redis template used for Redis health checks
     * @param kafkaAdmin kafka admin client used for broker health checks
     * @param meterRegistry metrics registry
     */
    public RegionalFailoverManager(
            DataSource dataSource,
            StringRedisTemplate redisTemplate,
            MeterRegistry meterRegistry,
            @Value("${app.multi-region.enabled:false}") boolean enabled,
            @Value("${app.multi-region.auto-failover.enabled:true}") boolean autoFailoverEnabled,
            @Value("${app.multi-region.auto-failover.unhealthy-threshold:3}") int globalUnhealthyThreshold,
            @Value("${app.multi-region.auto-failover.healthy-threshold:2}") int healthyThresholdToRecover,
            @Value("${app.multi-region.health.check-timeout-ms:2000}") long checkTimeoutMs,
            @Value("${app.multi-region.health.redis-latency-threshold-ms:150}") long redisLatencyThresholdMs,
            @Value("${app.multi-region.health.db-failure-threshold:2}") int dbFailureThreshold,
            @Value("${app.multi-region.health.redis-failure-threshold:2}") int redisFailureThreshold,
            @Value("${app.multi-region.health.kafka-failure-threshold:2}") int kafkaFailureThreshold,
            @Value("${app.multi-region.region-id:region-a}") String regionId,
            @Value("${app.multi-region.failover.mode:active-passive}") String failoverStrategy,
            @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String kafkaBootstrapServers) {
        this.dataSource = dataSource;
        this.redisTemplate = redisTemplate;
        this.meterRegistry = meterRegistry;
        this.enabled = enabled;
        this.autoFailoverEnabled = autoFailoverEnabled;
        this.globalUnhealthyThreshold = Math.max(1, globalUnhealthyThreshold);
        this.healthyThresholdToRecover = Math.max(1, healthyThresholdToRecover);
        this.checkTimeoutMs = Math.max(500, checkTimeoutMs);
        this.redisLatencyThresholdMs = Math.max(1, redisLatencyThresholdMs);
        this.dbFailureThreshold = Math.max(1, dbFailureThreshold);
        this.redisFailureThreshold = Math.max(1, redisFailureThreshold);
        this.kafkaFailureThreshold = Math.max(1, kafkaFailureThreshold);
        this.regionId = regionId;
        this.failoverStrategy = failoverStrategy == null || failoverStrategy.isBlank()
                ? "active-passive" : failoverStrategy.toLowerCase();
        this.nodeState = new AtomicReference<>(NodeState.ACTIVE);
        this.kafkaBootstrapServers = kafkaBootstrapServers;
        this.failoverEventsCounter = meterRegistry.counter("failover.events.count", "region", regionId);
        this.redisSlowChecksCounter = meterRegistry.counter("region.health.redis.slow.count", "region", regionId);
        this.adminClient = createAdminClient();
    }

    /**
     * Determines whether write operations are currently allowed in this region.
     *
     * <p>When multi-region is disabled, writes are always allowed. When enabled, PASSIVE mode
     * blocks writes to reduce split-brain and inconsistent state during failover.</p>
     *
     * @return {@code true} if callers may execute write paths, otherwise {@code false}
     */
    public boolean allowsWrites() {
        if (!enabled) {
            return true;
        }
        return nodeState.get() != NodeState.PASSIVE;
    }

    /**
     * Exposes current failover strategy and node mode for diagnostics.
     *
     * @return mode string in format {@code strategy:state}
     */
    public String currentMode() {
        return failoverStrategy + ":" + nodeState.get().name().toLowerCase();
    }

    @Scheduled(fixedDelayString = "${app.multi-region.auto-failover.poll-ms:5000}")
    /**
     * Runs periodic dependency health checks and applies failover/recovery policy.
     *
     * <p>Switching policy uses streak thresholds to avoid mode flapping on transient incidents.</p>
     */
    public void monitorAndFailover() {
        if (!enabled || !autoFailoverEnabled) {
            return;
        }
        boolean dbHealthy = checkDb();
        boolean redisHealthy = checkRedis();
        boolean kafkaHealthy = checkKafka();
        boolean healthy = dbHealthy && redisHealthy && kafkaHealthy;

        updateDependencyFailureCounter("db", dbHealthy, dbFailures);
        updateDependencyFailureCounter("redis", redisHealthy, redisFailures);
        updateDependencyFailureCounter("kafka", kafkaHealthy, kafkaFailures);

        boolean dependencyThresholdBreached = dbFailures.get() >= dbFailureThreshold
                || redisFailures.get() >= redisFailureThreshold
                || kafkaFailures.get() >= kafkaFailureThreshold;
        if (healthy) {
            consecutiveUnhealthy.set(0);
            int healthyStreak = consecutiveHealthy.incrementAndGet();
            if (nodeState.get() == NodeState.PASSIVE
                    && healthyStreak >= healthyThresholdToRecover
                    && "active-passive".equalsIgnoreCase(failoverStrategy)) {
                switchMode(NodeState.ACTIVE, "dependencies-restored");
            }
            return;
        }

        consecutiveHealthy.set(0);
        int failures = consecutiveUnhealthy.incrementAndGet();
        meterRegistry.counter("region.health.unhealthy.count", "region", regionId).increment();
        if (dependencyThresholdBreached
                && failures >= globalUnhealthyThreshold
                && "active-passive".equalsIgnoreCase(failoverStrategy)) {
            switchMode(NodeState.PASSIVE, "dependency-health-threshold-reached");
        }
    }

    private void switchMode(NodeState nextMode, String reason) {
        NodeState previous = nodeState.getAndSet(nextMode);
        if (previous == nextMode) {
            return;
        }
        failoverEventsCounter.increment();
        log.warn("Region failover mode switched region={} from={} to={} reason={}",
                regionId, previous.name().toLowerCase(), nextMode.name().toLowerCase(), reason);
    }

    private boolean checkDb() {
        try (Connection connection = dataSource.getConnection()) {
            int seconds = (int) Math.max(1, TimeUnit.MILLISECONDS.toSeconds(checkTimeoutMs));
            return connection.isValid(seconds);
        } catch (Exception ex) {
            log.debug("Regional DB health check failed region={} reason={}", regionId, ex.toString());
            return false;
        }
    }

    private boolean checkRedis() {
        try (RedisConnection connection = redisTemplate.getConnectionFactory().getConnection()) {
            long startNs = System.nanoTime();
            String pong = connection.ping();
            long latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);
            meterRegistry.timer("region.health.redis.latency", "region", regionId)
                    .record(latencyMs, TimeUnit.MILLISECONDS);
            if (latencyMs > redisLatencyThresholdMs) {
                redisSlowChecksCounter.increment();
                log.warn("Regional Redis health check slow region={} latencyMs={} thresholdMs={}",
                        regionId, latencyMs, redisLatencyThresholdMs);
            }
            return pong != null && "PONG".equalsIgnoreCase(pong);
        } catch (Exception ex) {
            log.debug("Regional Redis health check failed region={} reason={}", regionId, ex.toString());
            return false;
        }
    }

    private boolean checkKafka() {
        try {
            DescribeClusterResult cluster = adminClient.describeCluster();
            String clusterId = cluster.clusterId().get(checkTimeoutMs, TimeUnit.MILLISECONDS);
            int nodeCount = cluster.nodes().get(checkTimeoutMs, TimeUnit.MILLISECONDS).size();
            Object controller = cluster.controller().get(checkTimeoutMs, TimeUnit.MILLISECONDS);
            // Validate cluster metadata and topic-listing call for stronger signal than connectivity only.
            adminClient.listTopics().names().get(checkTimeoutMs, TimeUnit.MILLISECONDS);
            if (clusterId == null || clusterId.isBlank() || nodeCount <= 0 || controller == null) {
                log.warn("Regional Kafka health degraded region={} clusterId={} nodeCount={} controllerPresent={}",
                        regionId, clusterId, nodeCount, controller != null);
                return false;
            }
            return true;
        } catch (Exception ex) {
            log.debug("Regional Kafka health check failed region={} reason={}", regionId, ex.toString());
            return false;
        }
    }

    private void updateDependencyFailureCounter(String dependency, boolean healthy, AtomicInteger counter) {
        if (healthy) {
            counter.set(0);
            return;
        }
        int current = counter.incrementAndGet();
        meterRegistry.counter("region.health.dependency.failure.count", "region", regionId, "dependency", dependency)
                .increment();
        if (current == 1
                || current == dbFailureThreshold
                || current == redisFailureThreshold
                || current == kafkaFailureThreshold) {
            log.warn("Regional dependency unhealthy region={} dependency={} consecutiveFailures={}",
                    regionId, dependency, current);
        }
    }

    private AdminClient createAdminClient() {
        Properties props = new Properties();
        props.put("bootstrap.servers", kafkaBootstrapServers);
        props.put("request.timeout.ms", String.valueOf(checkTimeoutMs));
        props.put("default.api.timeout.ms", String.valueOf(checkTimeoutMs));
        props.put("connections.max.idle.ms", "30000");
        return AdminClient.create(props);
    }

    @PreDestroy
    /**
     * Closes Kafka admin resources on container shutdown.
     */
    public void shutdown() {
        try {
            adminClient.close(Duration.ofMillis(checkTimeoutMs));
        } catch (Exception ex) {
            log.debug("Kafka admin client close warning region={} reason={}", regionId, ex.toString());
        }
    }
}
