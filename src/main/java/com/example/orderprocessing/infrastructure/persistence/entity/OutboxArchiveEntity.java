package com.example.orderprocessing.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_events_archive")
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

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getAggregateType() { return aggregateType; }
    public void setAggregateType(String aggregateType) { this.aggregateType = aggregateType; }
    public String getAggregateId() { return aggregateId; }
    public void setAggregateId(String aggregateId) { this.aggregateId = aggregateId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }
    public Integer getPartitionKey() { return partitionKey; }
    public void setPartitionKey(Integer partitionKey) { this.partitionKey = partitionKey; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public Instant getArchivedAt() { return archivedAt; }
    public void setArchivedAt(Instant archivedAt) { this.archivedAt = archivedAt; }
}
