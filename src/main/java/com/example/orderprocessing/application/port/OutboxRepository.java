package com.example.orderprocessing.application.port;

import com.example.orderprocessing.infrastructure.persistence.entity.OutboxEntity;
import com.example.orderprocessing.infrastructure.persistence.entity.OutboxStatus;
import java.util.List;
import java.util.UUID;

public interface OutboxRepository {
    OutboxEntity save(OutboxEntity outboxEvent);
    List<OutboxEntity> findTop100ByStatusInOrderByCreatedAtAsc(List<OutboxStatus> statuses);
    long countByStatus(OutboxStatus status);
    boolean existsByIdAndStatus(UUID id, OutboxStatus status);
}
