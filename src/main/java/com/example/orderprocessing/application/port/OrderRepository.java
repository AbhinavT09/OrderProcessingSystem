package com.example.orderprocessing.application.port;

import com.example.orderprocessing.domain.model.OrderStatus;
import com.example.orderprocessing.infrastructure.persistence.entity.OrderEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * OrderRepository interface defines a stable boundary used by collaborating components.
 * It is used to keep the boots the Spring runtime for the service layer explicit and maintainable in this architecture.
 */
public interface OrderRepository {
    /**
     * Performs save.
     * @param order input argument used by this operation
     * @return operation result
     */
    OrderEntity save(OrderEntity order);
    /**
     * Performs findById.
     * @param id input argument used by this operation
     * @return operation result
     */
    Optional<OrderEntity> findById(UUID id);
    /**
     * Performs findAll.
     * @return operation result
     */
    List<OrderEntity> findAll();
    /**
     * Performs findByStatus.
     * @param status input argument used by this operation
     * @return operation result
     */
    List<OrderEntity> findByStatus(OrderStatus status);
    /**
     * Performs findByIdempotencyKey.
     * @param idempotencyKey input argument used by this operation
     * @return operation result
     */
    Optional<OrderEntity> findByIdempotencyKey(String idempotencyKey);
}
