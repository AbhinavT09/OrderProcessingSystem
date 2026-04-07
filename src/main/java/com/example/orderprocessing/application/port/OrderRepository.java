package com.example.orderprocessing.application.port;

import com.example.orderprocessing.domain.order.OrderStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Hexagonal application port for order persistence operations.
 *
 * <p>Decouples application/domain services from concrete storage technology while exposing
 * query capabilities needed by command and read services.</p>
 *
 * <p><b>Idempotency context:</b> {@link #findByIdempotencyKey(String)} is used by write-side
 * orchestration to guarantee that repeated create requests resolve to the same aggregate.</p>
 *
 * <p><b>Transactional context:</b> implementations are invoked inside service-level DB
 * transactions. This port does not include Kafka side effects.</p>
 */
public interface OrderRepository {
    /**
     * Persists an order snapshot.
     *
     * @param order entity representation of current aggregate state
     * @return persisted entity (including generated/updated persistence fields)
     */
    OrderRecord save(OrderRecord order);
    /**
     * Loads an order by identifier.
     *
     * @param id order identifier
     * @return matching entity when present
     */
    Optional<OrderRecord> findById(UUID id);
    /**
     * Lists all persisted orders.
     *
     * @return all order entities
     */
    List<OrderRecord> findAll();
    /**
     * Lists orders by status.
     *
     * @param status status filter
     * @return entities matching requested status
     */
    List<OrderRecord> findByStatus(OrderStatus status);
    /**
     * Reads one bounded page of orders.
     *
     * @param pageable page and size constraints
     * @return page of order entities
     */
    Page<OrderRecord> findAll(Pageable pageable);
    /**
     * Reads one bounded page of orders by status.
     *
     * @param status status filter
     * @param pageable page and size constraints
     * @return page of order entities matching status
     */
    Page<OrderRecord> findByStatus(OrderStatus status, Pageable pageable);
    /**
     * Resolves order previously created for an idempotency key.
     *
     * @param idempotencyKey external request idempotency key
     * @return matching order when key has a stored mapping
     */
    Optional<OrderRecord> findByIdempotencyKey(String idempotencyKey);
    /**
     * Loads an order when it belongs to the given owner principal (JWT {@code sub}).
     */
    Optional<OrderRecord> findByIdAndOwnerSubject(UUID id, String ownerSubject);
    /**
     * Page of orders for a single owner.
     */
    Page<OrderRecord> findByOwnerSubject(String ownerSubject, Pageable pageable);
    /**
     * Page of orders for a single owner filtered by status.
     */
    Page<OrderRecord> findByOwnerSubjectAndStatus(String ownerSubject, OrderStatus status, Pageable pageable);
}
