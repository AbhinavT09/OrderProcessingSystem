package com.example.orderprocessing.infrastructure.resilience;

import com.example.orderprocessing.infrastructure.persistence.entity.OrderEntity;
import com.example.orderprocessing.infrastructure.resilience.conflict.ConflictResolutionStrategy;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RegionalConsistencyManager {

    private final RegionalFailoverManager regionalFailoverManager;
    private final ConflictResolutionStrategy conflictResolutionStrategy;
    private final String regionId;
    private final Counter rejectedConflictCounter;

    public RegionalConsistencyManager(
            RegionalFailoverManager regionalFailoverManager,
            ConflictResolutionStrategy conflictResolutionStrategy,
            MeterRegistry meterRegistry,
            @Value("${app.multi-region.region-id:region-a}") String regionId) {
        this.regionalFailoverManager = regionalFailoverManager;
        this.conflictResolutionStrategy = conflictResolutionStrategy;
        this.regionId = regionId;
        this.rejectedConflictCounter = meterRegistry.counter(
                "region.conflict.rejected.count",
                "region",
                regionId);
    }

    public boolean allowsWrites() {
        return regionalFailoverManager.allowsWrites();
    }

    public String currentMode() {
        return regionalFailoverManager.currentMode();
    }

    public String regionId() {
        return regionId;
    }

    public boolean shouldApplyIncomingUpdate(OrderEntity current, String incomingRegionId, Instant incomingTimestamp, Long incomingVersion) {
        boolean decision = conflictResolutionStrategy.shouldApplyIncomingUpdate(
                current, incomingRegionId, incomingTimestamp, incomingVersion);
        if (!decision) {
            rejectedConflictCounter.increment();
        }
        return decision;
    }
}
