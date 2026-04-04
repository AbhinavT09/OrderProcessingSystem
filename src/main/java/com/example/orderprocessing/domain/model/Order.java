package com.example.orderprocessing.domain.model;

import com.example.orderprocessing.domain.state.OrderState;
import com.example.orderprocessing.domain.state.OrderStateFactory;
import com.example.orderprocessing.domain.state.PendingState;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

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

    public static Order create(List<OrderItem> items, String idempotencyKey) {
        return new Order(UUID.randomUUID(), Instant.now(), idempotencyKey, items, new PendingState(), null);
    }

    public static Order rehydrate(UUID id,
                                  Instant createdAt,
                                  String idempotencyKey,
                                  List<OrderItem> items,
                                  OrderStatus status,
                                  Long version) {
        return new Order(id, createdAt, idempotencyKey, items, OrderStateFactory.fromStatus(status), version);
    }

    public void updateStatus(OrderStatus target) {
        currentState = currentState.transitionTo(target);
    }

    public void cancel() {
        currentState = currentState.cancel();
    }

    public boolean promotePendingToProcessing() {
        OrderStatus before = currentState.status();
        currentState = currentState.promotePendingToProcessing();
        return before != currentState.status();
    }

    public UUID getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public OrderStatus getStatus() {
        return currentState.status();
    }

    public Long getVersion() {
        return version;
    }
}
