package com.example.orderprocessing.application.port;

import com.example.orderprocessing.domain.model.OrderStatus;
import com.example.orderprocessing.infrastructure.persistence.entity.OrderEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepositoryPort {
    OrderEntity save(OrderEntity order);
    Optional<OrderEntity> findById(UUID id);
    List<OrderEntity> findAll();
    List<OrderEntity> findByStatus(OrderStatus status);
    Optional<OrderEntity> findByIdempotencyKey(String idempotencyKey);
}
