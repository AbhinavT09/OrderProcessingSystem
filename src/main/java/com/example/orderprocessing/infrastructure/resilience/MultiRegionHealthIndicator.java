package com.example.orderprocessing.infrastructure.resilience;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("multiRegion")
public class MultiRegionHealthIndicator implements HealthIndicator {

    private final RegionalFailoverManager failoverManager;

    public MultiRegionHealthIndicator(RegionalFailoverManager failoverManager) {
        this.failoverManager = failoverManager;
    }

    @Override
    public Health health() {
        String mode = failoverManager.currentMode();
        boolean writable = failoverManager.allowsWrites();
        if (writable) {
            return Health.up().withDetail("mode", mode).withDetail("writesAllowed", true).build();
        }
        return Health.status("DEGRADED")
                .withDetail("mode", mode)
                .withDetail("writesAllowed", false)
                .build();
    }
}
