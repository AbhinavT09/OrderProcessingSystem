package com.example.orderprocessing.infrastructure.resilience;

import com.example.orderprocessing.application.port.OrderItemRecord;
import com.example.orderprocessing.application.port.OrderRecord;
import com.example.orderprocessing.domain.order.OrderStatus;
import com.example.orderprocessing.infrastructure.resilience.conflict.ConflictResolutionStrategy;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RegionalConsistencyManagerTest {

    @Test
    void allowsWrites_delegatesToFailoverManager() {
        RegionalFailoverManager failover = mock(RegionalFailoverManager.class);
        when(failover.allowsWrites()).thenReturn(false);
        ConflictResolutionStrategy strategy = mock(ConflictResolutionStrategy.class);
        RegionalConsistencyManager mgr = new RegionalConsistencyManager(
                failover, strategy, new SimpleMeterRegistry(), "r1");
        assertFalse(mgr.allowsWrites());
    }

    @Test
    void shouldApplyIncomingUpdate_delegatesToStrategy() {
        RegionalFailoverManager failover = mock(RegionalFailoverManager.class);
        when(failover.allowsWrites()).thenReturn(true);
        ConflictResolutionStrategy strategy = mock(ConflictResolutionStrategy.class);
        OrderRecord record = sampleRecord();
        Instant incomingTs = Instant.parse("2026-06-01T12:00:00Z");
        when(strategy.shouldApplyIncomingUpdate(record, "east", incomingTs, 1L)).thenReturn(true);
        RegionalConsistencyManager mgr = new RegionalConsistencyManager(
                failover, strategy, new SimpleMeterRegistry(), "r1");

        assertTrue(mgr.shouldApplyIncomingUpdate(record, "east", incomingTs, 1L));
    }

    @Test
    void shouldApplyIncomingUpdate_incrementsCounterWhenRejected() {
        RegionalFailoverManager failover = mock(RegionalFailoverManager.class);
        ConflictResolutionStrategy strategy = mock(ConflictResolutionStrategy.class);
        when(strategy.shouldApplyIncomingUpdate(any(), any(), any(), any())).thenReturn(false);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RegionalConsistencyManager mgr = new RegionalConsistencyManager(
                failover, strategy, registry, "r1");

        mgr.shouldApplyIncomingUpdate(sampleRecord(), "east", Instant.now(), 1L);

        assertEquals(1.0, registry.get("region.conflict.rejected.count").counter().count());
    }

    private static OrderRecord sampleRecord() {
        return new OrderRecord(
                UUID.randomUUID(),
                0L,
                OrderStatus.PENDING,
                Instant.now(),
                null,
                "o",
                "region-a",
                Instant.now(),
                List.of(new OrderItemRecord("x", 1, 1.0)));
    }
}
