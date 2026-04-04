package com.example.orderprocessing.infrastructure.resilience;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("multiRegion")
/**
 * MultiRegionHealthIndicator implements a concrete responsibility in the order processing service.
 * It is used to keep the boots the Spring runtime for the service layer explicit and maintainable in this architecture.
 */
public class MultiRegionHealthIndicator implements HealthIndicator {

    private final RegionalFailoverManager failoverManager;

    /**
     * Creates a health indicator bound to failover state.
     * @param failoverManager manager exposing regional mode and write state
     */
    public MultiRegionHealthIndicator(RegionalFailoverManager failoverManager) {
        this.failoverManager = failoverManager;
    }

    @Override
    /**
     * Executes health.
     * @return operation result
     */
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
