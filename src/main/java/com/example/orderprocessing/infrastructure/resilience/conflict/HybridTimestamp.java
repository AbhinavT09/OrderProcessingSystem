package com.example.orderprocessing.infrastructure.resilience.conflict;

import java.time.Instant;

/**
 * Hybrid logical clock tuple used for cross-region causal ordering.
 *
 * <p>Combines physical wall-clock time in milliseconds with a logical counter that
 * disambiguates events sharing the same physical millisecond.</p>
 */
public record HybridTimestamp(long physicalMillis, long logicalCounter) implements Comparable<HybridTimestamp> {

    public static HybridTimestamp from(Instant physical, Long logicalCounter) {
        long physicalMs = physical == null ? 0L : physical.toEpochMilli();
        long logical = logicalCounter == null ? 0L : Math.max(0L, logicalCounter);
        return new HybridTimestamp(physicalMs, logical);
    }

    @Override
    public int compareTo(HybridTimestamp other) {
        if (other == null) {
            return 1;
        }
        int physicalCmp = Long.compare(this.physicalMillis, other.physicalMillis);
        if (physicalCmp != 0) {
            return physicalCmp;
        }
        return Long.compare(this.logicalCounter, other.logicalCounter);
    }
}
