package com.example.orderprocessing.infrastructure.persistence.repository;

import com.example.orderprocessing.infrastructure.persistence.entity.ProcessedEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringProcessedEventJpaRepository extends JpaRepository<ProcessedEventEntity, Long> {
    boolean existsByEventId(String eventId);
}
