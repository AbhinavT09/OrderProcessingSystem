package com.example.orderprocessing.infrastructure.resilience.conflict;

import java.time.Instant;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HybridTimestampTest {

    @Test
    void from_nullPhysicalUsesZeroMillis() {
        HybridTimestamp ts = HybridTimestamp.from(null, 5L);
        assertEquals(0L, ts.physicalMillis());
        assertEquals(5L, ts.logicalCounter());
    }

    @Test
    void from_nullLogicalUsesZero() {
        HybridTimestamp ts = HybridTimestamp.from(Instant.EPOCH, null);
        assertEquals(0L, ts.logicalCounter());
    }

    @Test
    void compareTo_ordersByPhysicalThenLogical() {
        Instant t1 = Instant.ofEpochMilli(100);
        HybridTimestamp a = HybridTimestamp.from(t1, 1L);
        HybridTimestamp b = HybridTimestamp.from(t1, 2L);
        assertTrue(a.compareTo(b) < 0);
        HybridTimestamp earlier = HybridTimestamp.from(Instant.ofEpochMilli(99), 99L);
        assertTrue(earlier.compareTo(a) < 0);
    }

    @Test
    void compareTo_nullOtherIsGreater() {
        HybridTimestamp a = HybridTimestamp.from(Instant.now(), 0L);
        assertEquals(1, a.compareTo(null));
    }
}
