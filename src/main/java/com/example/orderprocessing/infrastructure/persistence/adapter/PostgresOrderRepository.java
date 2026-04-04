package com.example.orderprocessing.infrastructure.persistence.adapter;

import com.example.orderprocessing.application.port.OrderRepository;
import com.example.orderprocessing.domain.model.OrderStatus;
import com.example.orderprocessing.infrastructure.persistence.entity.OrderEntity;
import com.example.orderprocessing.infrastructure.persistence.repository.SpringOrderJpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class PostgresOrderRepository implements OrderRepository {

    private final SpringOrderJpaRepository repository;

    public PostgresOrderRepository(SpringOrderJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public OrderEntity save(OrderEntity order) {
        return repository.save(order);
    }

    @Override
    public Optional<OrderEntity> findById(UUID id) {
        return repository.findById(id);
    }

    @Override
    public List<OrderEntity> findAll() {
        return repository.findAll();
    }

    @Override
    public List<OrderEntity> findByStatus(OrderStatus status) {
        return repository.findByStatusOrderByCreatedAtAsc(status);
    }

    @Override
    public Optional<OrderEntity> findByIdempotencyKey(String idempotencyKey) {
        return repository.findByIdempotencyKey(idempotencyKey);
    }
}
