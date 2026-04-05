package com.example.orderprocessing.infrastructure.resilience;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("multiRegion")
/**
 * Actuator health adapter exposing regional write-mode state.
 *
 * <p><b>Architecture role:</b> infrastructure adapter over {@link RegionalFailoverManager} for
 * operator visibility and routing automation.</p>
 *
 * <p><b>Resilience context:</b> signals when write gating is active so control planes can steer
 * writes away from passive or degraded regions.</p>
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
