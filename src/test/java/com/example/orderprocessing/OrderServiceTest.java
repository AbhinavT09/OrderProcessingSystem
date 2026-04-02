package com.example.orderprocessing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.orderprocessing.model.CreateOrderRequest;
import com.example.orderprocessing.model.Order;
import com.example.orderprocessing.model.OrderItem;
import com.example.orderprocessing.model.OrderStatus;
import com.example.orderprocessing.repository.InMemoryOrderRepository;
import com.example.orderprocessing.service.OrderService;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrderServiceTest {

    @Test
    void shouldPromotePendingOrdersToProcessing() {
        OrderService orderService = new OrderService(new InMemoryOrderRepository());

        CreateOrderRequest request = new CreateOrderRequest();
        OrderItem item = new OrderItem();
        item.setProductName("Mouse");
        item.setQuantity(1);
        item.setPrice(25.0);
        request.setItems(List.of(item));

        Order order = orderService.createOrder(request);
        assertEquals(OrderStatus.PENDING, orderService.getOrderById(order.getId()).getStatus());

        orderService.promotePendingOrdersToProcessing();

        assertEquals(OrderStatus.PROCESSING, orderService.getOrderById(order.getId()).getStatus());
    }
}
