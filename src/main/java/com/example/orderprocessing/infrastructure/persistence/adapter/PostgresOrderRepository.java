package com.example.orderprocessing.infrastructure.persistence.adapter;

import com.example.orderprocessing.application.port.OrderRepository;
import com.example.orderprocessing.domain.order.OrderStatus;
import com.example.orderprocessing.infrastructure.persistence.entity.OrderEntity;
import com.example.orderprocessing.infrastructure.persistence.repository.SpringOrderJpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
/**
 * PostgresOrderRepository implements a concrete responsibility in the order processing service.
 * It is used to keep the boots the Spring runtime for the service layer explicit and maintainable in this architecture.
 */
public class PostgresOrderRepository implements OrderRepository {

    private final SpringOrderJpaRepository repository;

    /**
     * Creates a PostgreSQL order repository adapter.
     * @param repository Spring Data repository dependency
     */
    public PostgresOrderRepository(SpringOrderJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    /**
     * Executes save.
     * @param order input argument used by this operation
     * @return operation result
     */
    public OrderEntity save(OrderEntity order) {
        return repository.save(order);
    }

    @Override
    /**
     * Executes findById.
     * @param id input argument used by this operation
     * @return operation result
     */
    public Optional<OrderEntity> findById(UUID id) {
        return repository.findById(id);
    }

    @Override
    /**
     * Executes findAll.
     * @return operation result
     */
    public List<OrderEntity> findAll() {
        return repository.findAll();
    }

    @Override
    /**
     * Executes findByStatus.
     * @param status input argument used by this operation
     * @return operation result
     */
    public List<OrderEntity> findByStatus(OrderStatus status) {
        return repository.findByStatusOrderByCreatedAtAsc(status);
    }

    @Override
    /**
     * Executes findByIdempotencyKey.
     * @param idempotencyKey input argument used by this operation
     * @return operation result
     */
    public Optional<OrderEntity> findByIdempotencyKey(String idempotencyKey) {
        return repository.findByIdempotencyKey(idempotencyKey);
    }
}
