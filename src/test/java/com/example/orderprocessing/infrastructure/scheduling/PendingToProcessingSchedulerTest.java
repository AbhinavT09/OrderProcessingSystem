package com.example.orderprocessing.infrastructure.scheduling;

import com.example.orderprocessing.application.service.OrderService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PendingToProcessingSchedulerTest {

    @Test
    void promotePendingOrders_delegatesToOrderService() {
        OrderService orderService = mock(OrderService.class);
        PendingToProcessingScheduler scheduler = new PendingToProcessingScheduler(orderService);

        scheduler.promotePendingOrders();

        verify(orderService).promotePendingOrdersScheduled();
    }

    @Test
    void promotePendingOrders_swallowsRuntimeExceptionFromService() {
        OrderService orderService = mock(OrderService.class);
        doThrow(new RuntimeException("boom")).when(orderService).promotePendingOrdersScheduled();
        PendingToProcessingScheduler scheduler = new PendingToProcessingScheduler(orderService);

        scheduler.promotePendingOrders();

        verify(orderService).promotePendingOrdersScheduled();
    }
}
