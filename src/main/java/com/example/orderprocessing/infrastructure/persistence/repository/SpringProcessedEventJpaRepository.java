package com.example.orderprocessing.infrastructure.persistence.repository;

import com.example.orderprocessing.infrastructure.persistence.entity.ProcessedEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * SpringProcessedEventJpaRepository interface defines a stable boundary used by collaborating components.
 * It is used to keep the boots the Spring runtime for the service layer explicit and maintainable in this architecture.
 */
public interface SpringProcessedEventJpaRepository extends JpaRepository<ProcessedEventEntity, Long> {
    /**
     * Performs existsByEventId.
     * @param eventId input argument used by this operation
     * @return operation result
     */
    boolean existsByEventId(String eventId);
}
