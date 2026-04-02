package com.example.orderprocessing.repository;

import com.example.orderprocessing.model.Order;
import com.example.orderprocessing.model.OrderStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository {

    Order save(Order order);

    Optional<Order> findById(UUID id);

    List<Order> findAll();

    List<Order> findByStatus(OrderStatus status);
}
