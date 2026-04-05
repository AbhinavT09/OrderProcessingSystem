package com.example.orderprocessing.infrastructure.resilience.conflict;

import com.example.orderprocessing.application.port.OrderRecord;
import java.time.Instant;

public interface ConflictResolutionStrategy {

    boolean shouldApplyIncomingUpdate(OrderRecord current, String incomingRegionId, Instant incomingTimestamp, Long incomingVersion);
}
