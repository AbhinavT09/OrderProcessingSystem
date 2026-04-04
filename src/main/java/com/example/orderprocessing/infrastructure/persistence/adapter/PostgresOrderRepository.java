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
 * Infrastructure adapter that implements the order repository port via JPA/PostgreSQL.
 *
 * <p>Provides persistence operations used by command and query services while keeping storage
 * concerns isolated from application and domain layers.</p>
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
     * Persists an order snapshot.
     *
     * @param order entity state to write
     * @return persisted entity
     */
    public OrderEntity save(OrderEntity order) {
        return repository.save(order);
    }

    @Override
    /**
     * Loads an order by id.
     *
     * @param id order identifier
     * @return order entity when present
     */
    public Optional<OrderEntity> findById(UUID id) {
        return repository.findById(id);
    }

    @Override
    /**
     * Lists all orders.
     *
     * @return all persisted order entities
     */
    public List<OrderEntity> findAll() {
        return repository.findAll();
    }

    @Override
    /**
     * Lists orders in a specific status ordered by creation time.
     *
     * @param status status filter
     * @return matching order entities
     */
    public List<OrderEntity> findByStatus(OrderStatus status) {
        return repository.findByStatusOrderByCreatedAtAsc(status);
    }

    @Override
    /**
     * Resolves an order associated with an idempotency key.
     *
     * @param idempotencyKey external request key
     * @return matching order when key mapping exists
     */
    public Optional<OrderEntity> findByIdempotencyKey(String idempotencyKey) {
        return repository.findByIdempotencyKey(idempotencyKey);
    }
}
