package com.example.orderprocessing.infrastructure.persistence.entity;

import com.example.orderprocessing.domain.order.OrderStatus;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders", uniqueConstraints = {
        @UniqueConstraint(name = "uk_orders_idempotency_key", columnNames = "idempotencyKey")
})
/**
 * OrderEntity implements a concrete responsibility in the order processing service.
 * It is used to keep the boots the Spring runtime for the service layer explicit and maintainable in this architecture.
 */
public class OrderEntity {

    @Id
    private UUID id;

    @Version
    private Long version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(length = 128)
    private String idempotencyKey;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "order_items", joinColumns = @JoinColumn(name = "order_id"))
    private List<OrderItemEmbeddable> items = new ArrayList<>();

    /**
     * Returns id value.
     * @return operation result
     */
    public UUID getId() {
        return id;
    }

    /**
     * Sets id value.
     * @param id input argument used by this operation
     */
    public void setId(UUID id) {
        this.id = id;
    }

    /**
     * Returns version value.
     * @return operation result
     */
    public Long getVersion() {
        return version;
    }

    /**
     * Sets version value.
     * @param version input argument used by this operation
     */
    public void setVersion(Long version) {
        this.version = version;
    }

    /**
     * Returns status value.
     * @return operation result
     */
    public OrderStatus getStatus() {
        return status;
    }

    /**
     * Sets status value.
     * @param status input argument used by this operation
     */
    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    /**
     * Returns createdAt value.
     * @return operation result
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Sets createdAt value.
     * @param createdAt input argument used by this operation
     */
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * Returns idempotencyKey value.
     * @return operation result
     */
    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    /**
     * Sets idempotencyKey value.
     * @param idempotencyKey input argument used by this operation
     */
    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    /**
     * Returns items value.
     * @return operation result
     */
    public List<OrderItemEmbeddable> getItems() {
        return items;
    }

    /**
     * Sets items value.
     * @param items input argument used by this operation
     */
    public void setItems(List<OrderItemEmbeddable> items) {
        this.items = items;
    }
}
