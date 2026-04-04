package com.example.orderprocessing.infrastructure.persistence.repository;

import com.example.orderprocessing.domain.order.OrderStatus;
import com.example.orderprocessing.infrastructure.persistence.entity.OrderEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * SpringOrderJpaRepository interface defines a stable boundary used by collaborating components.
 * It is used to keep the boots the Spring runtime for the service layer explicit and maintainable in this architecture.
 */
public interface SpringOrderJpaRepository extends JpaRepository<OrderEntity, UUID> {
    /**
     * Performs findByStatusOrderByCreatedAtAsc.
     * @param status input argument used by this operation
     * @return operation result
     */
    List<OrderEntity> findByStatusOrderByCreatedAtAsc(OrderStatus status);
    /**
     * Performs findByIdempotencyKey.
     * @param idempotencyKey input argument used by this operation
     * @return operation result
     */
    Optional<OrderEntity> findByIdempotencyKey(String idempotencyKey);
}
