package com.example.orderprocessing.infrastructure.persistence.repository;

import com.example.orderprocessing.infrastructure.persistence.entity.ProcessedEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for processed-event dedup markers.
 *
 * <p>Used by consumers to enforce idempotent event handling across retries and rebalances.</p>
 */
public interface SpringProcessedEventJpaRepository extends JpaRepository<ProcessedEventEntity, Long> {
    /**
     * Checks if an event id has already been recorded as processed.
     *
     * @param eventId integration event identifier
     * @return true when marker exists
     */
    boolean existsByEventId(String eventId);
}
