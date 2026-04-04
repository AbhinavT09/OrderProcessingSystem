package com.example.orderprocessing.infrastructure.persistence.adapter;

import com.example.orderprocessing.application.port.OutboxRepository;
import com.example.orderprocessing.infrastructure.persistence.entity.OutboxEntity;
import com.example.orderprocessing.infrastructure.persistence.entity.OutboxStatus;
import com.example.orderprocessing.infrastructure.persistence.repository.SpringOutboxJpaRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class JpaOutboxRepository implements OutboxRepository {

    private final SpringOutboxJpaRepository repository;

    public JpaOutboxRepository(SpringOutboxJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public OutboxEntity save(OutboxEntity outboxEvent) {
        return repository.save(outboxEvent);
    }

    @Override
    public List<OutboxEntity> findTop100ByStatusInOrderByCreatedAtAsc(List<OutboxStatus> statuses) {
        return repository.findTop100ByStatusInOrderByCreatedAtAsc(statuses);
    }

    @Override
    public long countByStatus(OutboxStatus status) {
        return repository.countByStatus(status);
    }

    @Override
    public boolean existsByIdAndStatus(UUID id, OutboxStatus status) {
        return repository.existsByIdAndStatus(id, status);
    }
}
