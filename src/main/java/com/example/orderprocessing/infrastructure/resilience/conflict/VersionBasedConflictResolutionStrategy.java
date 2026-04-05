package com.example.orderprocessing.infrastructure.resilience.conflict;

import com.example.orderprocessing.application.port.OrderRecord;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class VersionBasedConflictResolutionStrategy implements ConflictResolutionStrategy {

    private final String defaultMode;

    public VersionBasedConflictResolutionStrategy(
            @Value("${app.multi-region.consistency.conflict-resolution:last-write-wins}") String defaultMode) {
        this.defaultMode = defaultMode == null ? "last-write-wins" : defaultMode.trim().toLowerCase();
    }

    @Override
    public boolean shouldApplyIncomingUpdate(
            OrderRecord current, String incomingRegionId, Instant incomingTimestamp, Long incomingVersion) {
        if (current == null) {
            return true;
        }
        Instant currentTimestamp = current.lastUpdatedTimestamp();
        if (incomingTimestamp == null || currentTimestamp == null) {
            return true;
        }

        // HLC ordering: (physicalMillis, logicalCounter)
        HybridTimestamp incoming = HybridTimestamp.from(incomingTimestamp, incomingVersion);
        HybridTimestamp persisted = HybridTimestamp.from(currentTimestamp, current.version());
        int hlcCmp = incoming.compareTo(persisted);
        if (hlcCmp > 0) {
            return true;
        }
        if (hlcCmp < 0) {
            return false;
        }

        // Deterministic tie-breaker when HLC tuples are exactly equal.
        if (incomingRegionId != null && current.regionId() != null) {
            return incomingRegionId.compareTo(current.regionId()) >= 0;
        }
        return "version-based".equals(defaultMode);
    }
}
