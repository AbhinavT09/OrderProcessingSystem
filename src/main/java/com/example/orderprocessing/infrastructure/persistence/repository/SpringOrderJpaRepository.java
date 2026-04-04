package com.example.orderprocessing.infrastructure.persistence.repository;

import com.example.orderprocessing.domain.model.OrderStatus;
import com.example.orderprocessing.infrastructure.persistence.entity.OrderEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringOrderJpaRepository extends JpaRepository<OrderEntity, UUID> {
    List<OrderEntity> findByStatusOrderByCreatedAtAsc(OrderStatus status);
    Optional<OrderEntity> findByIdempotencyKey(String idempotencyKey);
}
