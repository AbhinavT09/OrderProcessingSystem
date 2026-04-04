package com.example.orderprocessing.infrastructure.persistence.repository;

import com.example.orderprocessing.infrastructure.persistence.entity.OutboxEntity;
import com.example.orderprocessing.infrastructure.persistence.entity.OutboxStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringOutboxJpaRepository extends JpaRepository<OutboxEntity, UUID> {
    List<OutboxEntity> findTop100ByStatusInOrderByCreatedAtAsc(List<OutboxStatus> statuses);
    long countByStatus(OutboxStatus status);
    boolean existsByIdAndStatus(UUID id, OutboxStatus status);
}
