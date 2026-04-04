package com.example.orderprocessing.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_events", indexes = {
        @Index(name = "idx_outbox_partition_status_next_attempt", columnList = "partitionKey,status,nextAttemptAt"),
        @Index(name = "idx_outbox_created_at", columnList = "createdAt")
})
/**
 * OutboxEntity implements a concrete responsibility in the order processing service.
 * It is used to keep the boots the Spring runtime for the service layer explicit and maintainable in this architecture.
 */
public class OutboxEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 64)
    private String aggregateType;

    @Column(nullable = false, length = 64)
    private String aggregateId;

    @Column(nullable = false, length = 128)
    private String eventType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OutboxStatus status;

    @Column(nullable = false)
    private Integer retryCount;

    @Column(nullable = false)
    private Integer partitionKey;

    @Column(nullable = false)
    private Instant nextAttemptAt;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    /**
     * Executes prePersist.
     */
    public void prePersist() {
        Instant now = Instant.now();
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (retryCount == null) {
            retryCount = 0;
        }
        if (partitionKey == null) {
            partitionKey = 0;
        }
        if (status == null) {
            status = OutboxStatus.PENDING;
        }
        if (nextAttemptAt == null) {
            nextAttemptAt = now;
        }
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    /**
     * Executes preUpdate.
     */
    public void preUpdate() {
        updatedAt = Instant.now();
    }

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
     * Returns aggregateType value.
     * @return operation result
     */
    public String getAggregateType() {
        return aggregateType;
    }

    /**
     * Sets aggregateType value.
     * @param aggregateType input argument used by this operation
     */
    public void setAggregateType(String aggregateType) {
        this.aggregateType = aggregateType;
    }

    /**
     * Returns aggregateId value.
     * @return operation result
     */
    public String getAggregateId() {
        return aggregateId;
    }

    /**
     * Sets aggregateId value.
     * @param aggregateId input argument used by this operation
     */
    public void setAggregateId(String aggregateId) {
        this.aggregateId = aggregateId;
    }

    /**
     * Returns eventType value.
     * @return operation result
     */
    public String getEventType() {
        return eventType;
    }

    /**
     * Sets eventType value.
     * @param eventType input argument used by this operation
     */
    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    /**
     * Returns payload value.
     * @return operation result
     */
    public String getPayload() {
        return payload;
    }

    /**
     * Sets payload value.
     * @param payload input argument used by this operation
     */
    public void setPayload(String payload) {
        this.payload = payload;
    }

    /**
     * Returns status value.
     * @return operation result
     */
    public OutboxStatus getStatus() {
        return status;
    }

    /**
     * Sets status value.
     * @param status input argument used by this operation
     */
    public void setStatus(OutboxStatus status) {
        this.status = status;
    }

    /**
     * Returns retryCount value.
     * @return operation result
     */
    public Integer getRetryCount() {
        return retryCount;
    }

    /**
     * Sets retryCount value.
     * @param retryCount input argument used by this operation
     */
    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
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
     * Returns partitionKey value.
     * @return operation result
     */
    public Integer getPartitionKey() {
        return partitionKey;
    }

    /**
     * Sets partitionKey value.
     * @param partitionKey input argument used by this operation
     */
    public void setPartitionKey(Integer partitionKey) {
        this.partitionKey = partitionKey;
    }

    /**
     * Returns nextAttemptAt value.
     * @return operation result
     */
    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    /**
     * Sets nextAttemptAt value.
     * @param nextAttemptAt input argument used by this operation
     */
    public void setNextAttemptAt(Instant nextAttemptAt) {
        this.nextAttemptAt = nextAttemptAt;
    }

    /**
     * Returns updatedAt value.
     * @return operation result
     */
    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Sets updatedAt value.
     * @param updatedAt input argument used by this operation
     */
    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
