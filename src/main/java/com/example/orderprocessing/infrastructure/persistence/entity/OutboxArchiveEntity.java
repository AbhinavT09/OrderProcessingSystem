package com.example.orderprocessing.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_events_archive")
/**
 * OutboxArchiveEntity implements a concrete responsibility in the order processing service.
 * It is used to keep the boots the Spring runtime for the service layer explicit and maintainable in this architecture.
 */
public class OutboxArchiveEntity {

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

    @Column(nullable = false)
    private Integer retryCount;

    @Column(nullable = false)
    private Integer partitionKey;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Column(nullable = false)
    private Instant archivedAt;

    /**
     * Executes from.
     * @param source input argument used by this operation
     * @param archivedAt input argument used by this operation
     * @return operation result
     */
    public static OutboxArchiveEntity from(OutboxEntity source, Instant archivedAt) {
        OutboxArchiveEntity archive = new OutboxArchiveEntity();
        archive.setId(source.getId());
        archive.setAggregateType(source.getAggregateType());
        archive.setAggregateId(source.getAggregateId());
        archive.setEventType(source.getEventType());
        archive.setPayload(source.getPayload());
        archive.setRetryCount(source.getRetryCount());
        archive.setPartitionKey(source.getPartitionKey());
        archive.setCreatedAt(source.getCreatedAt());
        archive.setUpdatedAt(source.getUpdatedAt());
        archive.setArchivedAt(archivedAt);
        return archive;
    }

    /**
     * Returns id value.
     * @return operation result
     */
    public UUID getId() { return id; }
    /**
     * Sets id value.
     * @param id input argument used by this operation
     */
    public void setId(UUID id) { this.id = id; }
    /**
     * Returns aggregateType value.
     * @return operation result
     */
    public String getAggregateType() { return aggregateType; }
    /**
     * Sets aggregateType value.
     * @param aggregateType input argument used by this operation
     */
    public void setAggregateType(String aggregateType) { this.aggregateType = aggregateType; }
    /**
     * Returns aggregateId value.
     * @return operation result
     */
    public String getAggregateId() { return aggregateId; }
    /**
     * Sets aggregateId value.
     * @param aggregateId input argument used by this operation
     */
    public void setAggregateId(String aggregateId) { this.aggregateId = aggregateId; }
    /**
     * Returns eventType value.
     * @return operation result
     */
    public String getEventType() { return eventType; }
    /**
     * Sets eventType value.
     * @param eventType input argument used by this operation
     */
    public void setEventType(String eventType) { this.eventType = eventType; }
    /**
     * Returns payload value.
     * @return operation result
     */
    public String getPayload() { return payload; }
    /**
     * Sets payload value.
     * @param payload input argument used by this operation
     */
    public void setPayload(String payload) { this.payload = payload; }
    /**
     * Returns retryCount value.
     * @return operation result
     */
    public Integer getRetryCount() { return retryCount; }
    /**
     * Sets retryCount value.
     * @param retryCount input argument used by this operation
     */
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }
    /**
     * Returns partitionKey value.
     * @return operation result
     */
    public Integer getPartitionKey() { return partitionKey; }
    /**
     * Sets partitionKey value.
     * @param partitionKey input argument used by this operation
     */
    public void setPartitionKey(Integer partitionKey) { this.partitionKey = partitionKey; }
    /**
     * Returns createdAt value.
     * @return operation result
     */
    public Instant getCreatedAt() { return createdAt; }
    /**
     * Sets createdAt value.
     * @param createdAt input argument used by this operation
     */
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    /**
     * Returns updatedAt value.
     * @return operation result
     */
    public Instant getUpdatedAt() { return updatedAt; }
    /**
     * Sets updatedAt value.
     * @param updatedAt input argument used by this operation
     */
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    /**
     * Returns archivedAt value.
     * @return operation result
     */
    public Instant getArchivedAt() { return archivedAt; }
    /**
     * Sets archivedAt value.
     * @param archivedAt input argument used by this operation
     */
    public void setArchivedAt(Instant archivedAt) { this.archivedAt = archivedAt; }
}
