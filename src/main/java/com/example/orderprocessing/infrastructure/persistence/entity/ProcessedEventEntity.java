package com.example.orderprocessing.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(name = "processed_events", uniqueConstraints = {
        @UniqueConstraint(name = "uk_processed_event_id", columnNames = "eventId")
})
/**
 * ProcessedEventEntity implements a concrete responsibility in the order processing service.
 * It is used to keep the boots the Spring runtime for the service layer explicit and maintainable in this architecture.
 */
public class ProcessedEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String eventId;

    @Column(nullable = false)
    private String eventType;

    @Column(nullable = false)
    private Instant processedAt;

    /**
     * Returns id value.
     * @return operation result
     */
    public Long getId() {
        return id;
    }

    /**
     * Sets id value.
     * @param id input argument used by this operation
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * Returns eventId value.
     * @return operation result
     */
    public String getEventId() {
        return eventId;
    }

    /**
     * Sets eventId value.
     * @param eventId input argument used by this operation
     */
    public void setEventId(String eventId) {
        this.eventId = eventId;
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
     * Returns processedAt value.
     * @return operation result
     */
    public Instant getProcessedAt() {
        return processedAt;
    }

    /**
     * Sets processedAt value.
     * @param processedAt input argument used by this operation
     */
    public void setProcessedAt(Instant processedAt) {
        this.processedAt = processedAt;
    }
}
