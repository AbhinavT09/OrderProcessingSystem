package com.example.orderprocessing.infrastructure.resilience;

import com.example.orderprocessing.application.port.OutboxRepository;
import com.example.orderprocessing.infrastructure.persistence.entity.OutboxStatus;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Resilience / "chaos-adjacent" unit coverage: {@link BackpressureManager} classification when
 * outbox backlog and DB pool saturation cross configured thresholds (no real network faults).
 */
@ExtendWith(MockitoExtension.class)
class BackpressureManagerResilienceTest {

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private HikariDataSource dataSource;

    @Mock
    private HikariPoolMXBean poolMxBean;

    @Test
    void elevatedWhenOutboxBacklogCrossesElevatedOnly() {
        when(outboxRepository.countByStatus(OutboxStatus.PENDING)).thenReturn(2500L);
        when(outboxRepository.countByStatus(OutboxStatus.FAILED)).thenReturn(0L);
        when(dataSource.getHikariPoolMXBean()).thenReturn(poolMxBean);
        when(poolMxBean.getTotalConnections()).thenReturn(10);
        when(poolMxBean.getActiveConnections()).thenReturn(1);

        BackpressureManager mgr = new BackpressureManager(
                outboxRepository,
                dataSource,
                new SimpleMeterRegistry(),
                2000,
                10000,
                120000,
                600000,
                0.75,
                0.90);
        mgr.refresh();

        assertEquals(BackpressureManager.Level.ELEVATED, mgr.level());
        assertFalse(mgr.shouldRejectWrites());
        assertEquals(0.70D, mgr.throttlingFactor(), 1e-9);
    }

    @Test
    void criticalWhenBacklogHitsCriticalThreshold() {
        when(outboxRepository.countByStatus(OutboxStatus.PENDING)).thenReturn(10000L);
        when(outboxRepository.countByStatus(OutboxStatus.FAILED)).thenReturn(0L);
        when(dataSource.getHikariPoolMXBean()).thenReturn(poolMxBean);
        when(poolMxBean.getTotalConnections()).thenReturn(10);
        when(poolMxBean.getActiveConnections()).thenReturn(1);

        BackpressureManager mgr = new BackpressureManager(
                outboxRepository,
                dataSource,
                new SimpleMeterRegistry(),
                2000,
                10000,
                120000,
                600000,
                0.75,
                0.90);
        mgr.refresh();

        assertEquals(BackpressureManager.Level.CRITICAL, mgr.level());
        assertTrue(mgr.shouldRejectWrites());
        assertEquals(0.35D, mgr.throttlingFactor(), 1e-9);
    }

    @Test
    void kafkaLagAloneCanElevate() {
        when(outboxRepository.countByStatus(OutboxStatus.PENDING)).thenReturn(0L);
        when(outboxRepository.countByStatus(OutboxStatus.FAILED)).thenReturn(0L);
        when(dataSource.getHikariPoolMXBean()).thenReturn(poolMxBean);
        when(poolMxBean.getTotalConnections()).thenReturn(10);
        when(poolMxBean.getActiveConnections()).thenReturn(1);

        BackpressureManager mgr = new BackpressureManager(
                outboxRepository,
                dataSource,
                new SimpleMeterRegistry(),
                2000,
                10000,
                120000,
                600000,
                0.75,
                0.90);
        mgr.recordKafkaLagMs(130000);
        mgr.refresh();

        assertEquals(BackpressureManager.Level.ELEVATED, mgr.level());
    }
}
