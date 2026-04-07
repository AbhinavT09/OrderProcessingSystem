package com.example.orderprocessing.infrastructure.persistence.repository;

import com.example.orderprocessing.domain.order.OrderStatus;
import com.example.orderprocessing.infrastructure.persistence.entity.OrderEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for primary order table access.
 *
 * <p>Provides query shapes required by application ports while relying on JPA for transaction
 * participation and optimistic-lock support.</p>
 */
public interface SpringOrderJpaRepository extends JpaRepository<OrderEntity, UUID> {
    /**
     * Retrieves orders in a status sorted by creation timestamp.
     *
     * @param status status filter
     * @return ordered matching orders
     */
    List<OrderEntity> findByStatusOrderByCreatedAtAsc(OrderStatus status);
    /**
     * Reads one page of orders by status.
     *
     * @param status status filter
     * @param pageable page and size constraints
     * @return bounded result page
     */
    Page<OrderEntity> findByStatus(OrderStatus status, Pageable pageable);
    /**
     * Finds an order by persisted idempotency key.
     *
     * @param idempotencyKey idempotency key captured at create time
     * @return matching order when present
     */
    Optional<OrderEntity> findByIdempotencyKey(String idempotencyKey);

    Optional<OrderEntity> findByIdAndOwnerSubject(UUID id, String ownerSubject);

    Page<OrderEntity> findByOwnerSubject(String ownerSubject, Pageable pageable);

    Page<OrderEntity> findByOwnerSubjectAndStatus(String ownerSubject, OrderStatus status, Pageable pageable);
}
