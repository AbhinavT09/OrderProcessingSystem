package com.example.orderprocessing.domain.order;

import com.example.orderprocessing.domain.order.state.OrderState;
import com.example.orderprocessing.domain.order.state.OrderStateFactory;
import com.example.orderprocessing.domain.order.state.PendingState;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Order implements a concrete responsibility in the order processing service.
 * It is used to keep the boots the Spring runtime for the service layer explicit and maintainable in this architecture.
 */
public class Order {

    private final UUID id;
    private final Instant createdAt;
    private final String idempotencyKey;
    private final List<OrderItem> items;
    private final Long version;
    private OrderState currentState;

    private Order(UUID id,
                  Instant createdAt,
                  String idempotencyKey,
                  List<OrderItem> items,
                  OrderState currentState,
                  Long version) {
        this.id = id;
        this.createdAt = createdAt;
        this.idempotencyKey = idempotencyKey;
        this.items = items;
        this.currentState = currentState;
        this.version = version;
    }

    /**
     * Executes create.
     * @param items input argument used by this operation
     * @param idempotencyKey input argument used by this operation
     * @return operation result
     */
    public static Order create(List<OrderItem> items, String idempotencyKey) {
        return new Order(UUID.randomUUID(), Instant.now(), idempotencyKey, items, new PendingState(), null);
    }

    /**
     * Rehydrates an aggregate from persisted state.
     * @param id persisted order id
     * @param status persisted order status
     * @param createdAt persisted creation timestamp
     * @param idempotencyKey persisted idempotency key
     * @param items persisted order items
     * @param version persisted optimistic lock version
     * @return reconstructed order aggregate
     */
    public static Order rehydrate(UUID id,
                                  Instant createdAt,
                                  String idempotencyKey,
                                  List<OrderItem> items,
                                  OrderStatus status,
                                  Long version) {
        return new Order(id, createdAt, idempotencyKey, items, OrderStateFactory.fromStatus(status), version);
    }

    /**
     * Executes updateStatus.
     * @param target input argument used by this operation
     */
    public void updateStatus(OrderStatus target) {
        currentState = currentState.transitionTo(target);
    }

    /**
     * Executes cancel.
     */
    public void cancel() {
        currentState = currentState.cancel();
    }

    /**
     * Executes promotePendingToProcessing.
     * @return operation result
     */
    public boolean promotePendingToProcessing() {
        OrderStatus before = currentState.status();
        currentState = currentState.promotePendingToProcessing();
        return before != currentState.status();
    }

    /**
     * Returns id value.
     * @return operation result
     */
    public UUID getId() {
        return id;
    }

    /**
     * Returns createdAt value.
     * @return operation result
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Returns idempotencyKey value.
     * @return operation result
     */
    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    /**
     * Returns items value.
     * @return operation result
     */
    public List<OrderItem> getItems() {
        return items;
    }

    /**
     * Returns status value.
     * @return operation result
     */
    public OrderStatus getStatus() {
        return currentState.status();
    }

    /**
     * Returns version value.
     * @return operation result
     */
    public Long getVersion() {
        return version;
    }
}
