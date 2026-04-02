package com.example.orderprocessing.service;

import com.example.orderprocessing.exception.InvalidOrderStateException;
import com.example.orderprocessing.exception.OrderNotFoundException;
import com.example.orderprocessing.model.CreateOrderRequest;
import com.example.orderprocessing.model.Order;
import com.example.orderprocessing.model.OrderStatus;
import com.example.orderprocessing.repository.OrderRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class OrderService {

    private final OrderRepository orderRepository;

    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public Order createOrder(CreateOrderRequest request) {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setItems(request.getItems());
        order.setStatus(OrderStatus.PENDING);
        order.setCreatedAt(Instant.now());
        return orderRepository.save(order);
    }

    public Order getOrderById(UUID id) {
        return orderRepository.findById(id).orElseThrow(() -> new OrderNotFoundException(id));
    }

    public Order updateOrderStatus(UUID id, OrderStatus status) {
        Order order = getOrderById(id);
        order.setStatus(status);
        return orderRepository.save(order);
    }

    public List<Order> listOrders(OrderStatus status) {
        if (status == null) {
            return orderRepository.findAll();
        }
        return orderRepository.findByStatus(status);
    }

    public Order cancelOrder(UUID id) {
        Order order = getOrderById(id);
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new InvalidOrderStateException("Only PENDING orders can be cancelled");
        }
        order.setStatus(OrderStatus.CANCELLED);
        return orderRepository.save(order);
    }

    public void promotePendingOrdersToProcessing() {
        List<Order> pendingOrders = orderRepository.findByStatus(OrderStatus.PENDING);
        for (Order order : pendingOrders) {
            order.setStatus(OrderStatus.PROCESSING);
            orderRepository.save(order);
        }
    }
}
