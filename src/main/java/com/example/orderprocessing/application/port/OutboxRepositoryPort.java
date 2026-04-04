package com.example.orderprocessing.application.port;

import com.example.orderprocessing.infrastructure.persistence.entity.OutboxEventEntity;
import java.util.List;

public interface OutboxRepositoryPort {
    OutboxEventEntity save(OutboxEventEntity outboxEvent);
    List<OutboxEventEntity> findPendingForPublish();
    boolean claimForPublish(Long id);
}
