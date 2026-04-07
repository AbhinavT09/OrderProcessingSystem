package com.example.orderprocessing.infrastructure.resilience;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
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
 *
 * <p><b>Readiness / load balancing:</b> when the region is PASSIVE, this indicator reports
 * {@link Status#OUT_OF_SERVICE} so orchestrators and global load balancers can drain traffic from
 * the instance instead of routing requests that would fail with 5xx (split-brain / blind routing).</p>
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
        boolean passive = failoverManager.isPassiveRegion();
        if (!passive) {
            return Health.up()
                    .withDetail("mode", mode)
                    .withDetail("nodeState", "ACTIVE")
                    .withDetail("writesAllowed", true)
                    .build();
        }
        return Health.status(Status.OUT_OF_SERVICE)
                .withDetail("mode", mode)
                .withDetail("nodeState", "PASSIVE")
                .withDetail("writesAllowed", false)
                .build();
    }
}
