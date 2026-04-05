package com.example.orderprocessing.infrastructure.resilience.conflict;

import com.example.orderprocessing.infrastructure.persistence.entity.OrderEntity;
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
            OrderEntity current, String incomingRegionId, Instant incomingTimestamp, Long incomingVersion) {
        if (current == null) {
            return true;
        }
        if ("version-based".equals(defaultMode) && incomingVersion != null && current.getVersion() != null) {
            return incomingVersion >= current.getVersion();
        }
        Instant currentTimestamp = current.getLastUpdatedTimestamp();
        if (currentTimestamp == null || incomingTimestamp == null) {
            return true;
        }
        if (incomingTimestamp.isAfter(currentTimestamp)) {
            return true;
        }
        if (incomingTimestamp.equals(currentTimestamp) && incomingRegionId != null && current.getRegionId() != null) {
            return incomingRegionId.compareTo(current.getRegionId()) >= 0;
        }
        return false;
    }
}
