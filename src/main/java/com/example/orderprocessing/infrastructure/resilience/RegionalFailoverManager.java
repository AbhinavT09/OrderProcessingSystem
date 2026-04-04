package com.example.orderprocessing.infrastructure.resilience;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.Connection;
import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.apache.kafka.clients.admin.AdminClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RegionalFailoverManager {

    private static final Logger log = LoggerFactory.getLogger(RegionalFailoverManager.class);

    private final DataSource dataSource;
    private final StringRedisTemplate redisTemplate;
    private final MeterRegistry meterRegistry;
    private final boolean enabled;
    private final boolean autoFailoverEnabled;
    private final int unhealthyThreshold;
    private final String regionId;
    private final String failoverStrategy;
    private final String kafkaBootstrapServers;
    private final AtomicReference<String> nodeState;
    private final AtomicInteger consecutiveUnhealthy = new AtomicInteger(0);
    private final Counter failoverEventsCounter;

    public RegionalFailoverManager(
            DataSource dataSource,
            StringRedisTemplate redisTemplate,
            MeterRegistry meterRegistry,
            @Value("${app.multi-region.enabled:false}") boolean enabled,
            @Value("${app.multi-region.auto-failover.enabled:true}") boolean autoFailoverEnabled,
            @Value("${app.multi-region.auto-failover.unhealthy-threshold:3}") int unhealthyThreshold,
            @Value("${app.multi-region.region-id:region-a}") String regionId,
            @Value("${app.multi-region.failover.mode:active-passive}") String failoverStrategy,
            @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String kafkaBootstrapServers) {
        this.dataSource = dataSource;
        this.redisTemplate = redisTemplate;
        this.meterRegistry = meterRegistry;
        this.enabled = enabled;
        this.autoFailoverEnabled = autoFailoverEnabled;
        this.unhealthyThreshold = Math.max(1, unhealthyThreshold);
        this.regionId = regionId;
        this.failoverStrategy = failoverStrategy == null || failoverStrategy.isBlank()
                ? "active-passive" : failoverStrategy.toLowerCase();
        this.nodeState = new AtomicReference<>("active");
        this.kafkaBootstrapServers = kafkaBootstrapServers;
        this.failoverEventsCounter = meterRegistry.counter("failover.events.count", "region", regionId);
    }

    public boolean allowsWrites() {
        if (!enabled) {
            return true;
        }
        return !"passive".equalsIgnoreCase(nodeState.get());
    }

    public String currentMode() {
        return failoverStrategy + ":" + nodeState.get();
    }

    @Scheduled(fixedDelayString = "${app.multi-region.auto-failover.poll-ms:5000}")
    public void monitorAndFailover() {
        if (!enabled || !autoFailoverEnabled) {
            return;
        }
        boolean healthy = checkDb() && checkRedis() && checkKafka();
        if (healthy) {
            if (consecutiveUnhealthy.getAndSet(0) > 0 && "passive".equalsIgnoreCase(nodeState.get())) {
                switchMode("active", "dependencies-restored");
            }
            return;
        }

        int failures = consecutiveUnhealthy.incrementAndGet();
        meterRegistry.counter("region.health.unhealthy.count", "region", regionId).increment();
        if (failures >= unhealthyThreshold && "active-passive".equalsIgnoreCase(failoverStrategy)) {
            switchMode("passive", "dependency-health-threshold-reached");
        }
    }

    private void switchMode(String nextMode, String reason) {
        String previous = nodeState.getAndSet(nextMode);
        if (previous.equalsIgnoreCase(nextMode)) {
            return;
        }
        failoverEventsCounter.increment();
        log.warn("Region failover mode switched region={} from={} to={} reason={}", regionId, previous, nextMode, reason);
    }

    private boolean checkDb() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid((int) Duration.ofSeconds(2).toSeconds());
        } catch (Exception ex) {
            log.warn("Regional DB health check failed region={} reason={}", regionId, ex.toString());
            return false;
        }
    }

    private boolean checkRedis() {
        try (RedisConnection connection = redisTemplate.getConnectionFactory().getConnection()) {
            String pong = connection.ping();
            return pong != null;
        } catch (Exception ex) {
            log.warn("Regional Redis health check failed region={} reason={}", regionId, ex.toString());
            return false;
        }
    }

    private boolean checkKafka() {
        Properties props = new Properties();
        props.put("bootstrap.servers", kafkaBootstrapServers);
        props.put("request.timeout.ms", "2000");
        props.put("default.api.timeout.ms", "2000");
        try (AdminClient adminClient = AdminClient.create(props)) {
            adminClient.describeCluster().clusterId().get();
            return true;
        } catch (Exception ex) {
            log.warn("Regional Kafka health check failed region={} reason={}", regionId, ex.toString());
            return false;
        }
    }
}
