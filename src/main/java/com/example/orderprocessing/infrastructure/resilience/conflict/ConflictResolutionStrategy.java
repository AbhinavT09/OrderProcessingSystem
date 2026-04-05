package com.example.orderprocessing.infrastructure.resilience.conflict;

import com.example.orderprocessing.infrastructure.persistence.entity.OrderEntity;
import java.time.Instant;

public interface ConflictResolutionStrategy {

    boolean shouldApplyIncomingUpdate(OrderEntity current, String incomingRegionId, Instant incomingTimestamp, Long incomingVersion);
}
