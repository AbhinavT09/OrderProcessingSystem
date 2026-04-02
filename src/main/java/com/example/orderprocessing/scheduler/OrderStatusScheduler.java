package com.example.orderprocessing.scheduler;

import com.example.orderprocessing.service.OrderService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OrderStatusScheduler {

    private final OrderService orderService;

    public OrderStatusScheduler(OrderService orderService) {
        this.orderService = orderService;
    }

    @Scheduled(fixedRate = 300000)
    public void updatePendingOrders() {
        orderService.promotePendingOrdersToProcessing();
    }
}
