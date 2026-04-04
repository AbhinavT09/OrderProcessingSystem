package com.example.orderprocessing.application.port;

import com.example.orderprocessing.domain.order.OrderStatus;
import com.example.orderprocessing.infrastructure.persistence.entity.OrderEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Application port for order persistence operations.
 *
 * <p>Decouples application/domain services from concrete storage technology while exposing
 * query capabilities needed by command and read services.</p>
 */
public interface OrderRepository {
    /**
     * Persists an order snapshot.
     *
     * @param order entity representation of current aggregate state
     * @return persisted entity (including generated/updated persistence fields)
     */
    OrderEntity save(OrderEntity order);
    /**
     * Loads an order by identifier.
     *
     * @param id order identifier
     * @return matching entity when present
     */
    Optional<OrderEntity> findById(UUID id);
    /**
     * Lists all persisted orders.
     *
     * @return all order entities
     */
    List<OrderEntity> findAll();
    /**
     * Lists orders by status.
     *
     * @param status status filter
     * @return entities matching requested status
     */
    List<OrderEntity> findByStatus(OrderStatus status);
    /**
     * Resolves order previously created for an idempotency key.
     *
     * @param idempotencyKey external request idempotency key
     * @return matching order when key has a stored mapping
     */
    Optional<OrderEntity> findByIdempotencyKey(String idempotencyKey);
}
