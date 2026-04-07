package com.example.orderprocessing.infrastructure.resilience.conflict;

import com.example.orderprocessing.application.port.OrderItemRecord;
import com.example.orderprocessing.application.port.OrderRecord;
import com.example.orderprocessing.domain.order.OrderStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VersionBasedConflictResolutionStrategyTest {

    @Test
    void nullCurrent_alwaysApplies() {
        VersionBasedConflictResolutionStrategy s = new VersionBasedConflictResolutionStrategy("last-write-wins");
        assertTrue(s.shouldApplyIncomingUpdate(null, "r1", Instant.now(), 1L));
    }

    @Test
    void nullTimestamp_defaultsToApply() {
        VersionBasedConflictResolutionStrategy s = new VersionBasedConflictResolutionStrategy("last-write-wins");
        OrderRecord current = record("east", Instant.parse("2026-01-01T00:00:00Z"), 1L);
        assertTrue(s.shouldApplyIncomingUpdate(current, "west", null, 2L));
    }

    @Test
    void newerHybridTimestamp_applies() {
        VersionBasedConflictResolutionStrategy s = new VersionBasedConflictResolutionStrategy("last-write-wins");
        OrderRecord current = record("east", Instant.parse("2026-01-01T00:00:00Z"), 1L);
        Instant newer = Instant.parse("2026-01-02T00:00:00Z");
        assertTrue(s.shouldApplyIncomingUpdate(current, "west", newer, 0L));
    }

    @Test
    void olderHybridTimestamp_rejects() {
        VersionBasedConflictResolutionStrategy s = new VersionBasedConflictResolutionStrategy("last-write-wins");
        OrderRecord current = record("east", Instant.parse("2026-01-02T00:00:00Z"), 1L);
        Instant older = Instant.parse("2026-01-01T00:00:00Z");
        assertFalse(s.shouldApplyIncomingUpdate(current, "west", older, 0L));
    }

    @Test
    void tieBreak_usesRegionIdWhenBothPresent() {
        VersionBasedConflictResolutionStrategy s = new VersionBasedConflictResolutionStrategy("last-write-wins");
        Instant t = Instant.parse("2026-01-01T00:00:00Z");
        OrderRecord current = record("m-region", t, 1L);
        // Same HLC: incoming "z" >= "m-region" lexicographically -> true
        assertTrue(s.shouldApplyIncomingUpdate(current, "z-region", t, 1L));
        assertFalse(s.shouldApplyIncomingUpdate(current, "a-region", t, 1L));
    }

    private static OrderRecord record(String regionId, Instant lastUpdated, long version) {
        return new OrderRecord(
                UUID.randomUUID(),
                version,
                OrderStatus.PENDING,
                Instant.parse("2025-01-01T00:00:00Z"),
                null,
                "owner",
                regionId,
                lastUpdated,
                List.of(new OrderItemRecord("p", 1, 1.0)));
    }
}
