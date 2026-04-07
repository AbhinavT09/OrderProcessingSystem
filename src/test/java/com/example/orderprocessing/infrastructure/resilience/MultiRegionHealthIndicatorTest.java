package com.example.orderprocessing.infrastructure.resilience;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MultiRegionHealthIndicatorTest {

    @Test
    void healthIsUpWhenRegionActive() {
        RegionalFailoverManager mgr = mock(RegionalFailoverManager.class);
        when(mgr.isPassiveRegion()).thenReturn(false);
        when(mgr.currentMode()).thenReturn("active-passive:active");

        Health health = new MultiRegionHealthIndicator(mgr).health();

        assertEquals(Status.UP, health.getStatus());
    }

    @Test
    void healthIsOutOfServiceWhenRegionPassive() {
        RegionalFailoverManager mgr = mock(RegionalFailoverManager.class);
        when(mgr.isPassiveRegion()).thenReturn(true);
        when(mgr.currentMode()).thenReturn("active-passive:passive");

        Health health = new MultiRegionHealthIndicator(mgr).health();

        assertEquals(Status.OUT_OF_SERVICE, health.getStatus());
    }
}
