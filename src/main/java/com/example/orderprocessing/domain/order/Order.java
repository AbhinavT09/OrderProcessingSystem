package com.example.orderprocessing.domain.order;

import com.example.orderprocessing.domain.order.state.OrderState;
import com.example.orderprocessing.domain.order.state.OrderStateFactory;
import com.example.orderprocessing.domain.order.state.PendingState;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Domain aggregate root for order lifecycle behavior.
 *
 * <p>Encapsulates order identity, item data, and state-transition rules via the state pattern.
 * This class belongs to the domain layer and is intentionally persistence-agnostic.</p>
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
     * Creates a new order in the initial {@code PENDING} lifecycle state.
     *
     * @param items order line items
     * @param idempotencyKey optional request key captured for duplicate suppression
     * @return newly constructed order aggregate
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
     * Applies a domain status transition.
     *
     * @param target desired target status
     */
    public void updateStatus(OrderStatus target) {
        currentState = currentState.transitionTo(target);
    }

    /**
     * Cancels the order when current state allows cancellation.
     */
    public void cancel() {
        currentState = currentState.cancel();
    }

    /**
     * Promotes a pending order to processing when applicable.
     *
     * @return {@code true} when promotion changed state, otherwise {@code false}
     */
    public boolean promotePendingToProcessing() {
        OrderStatus before = currentState.status();
        currentState = currentState.promotePendingToProcessing();
        return before != currentState.status();
    }

    /** Returns aggregate identifier. */
    public UUID getId() {
        return id;
    }

    /** Returns creation timestamp captured at aggregate creation. */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /** Returns optional idempotency key associated with create request. */
    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    /** Returns immutable order item collection. */
    public List<OrderItem> getItems() {
        return items;
    }

    /** Returns current lifecycle status exposed by active state object. */
    public OrderStatus getStatus() {
        return currentState.status();
    }

    /** Returns optimistic-lock version from persisted aggregate snapshot. */
    public Long getVersion() {
        return version;
    }
}
