package com.example.orderprocessing.infrastructure.persistence.adapter;

import com.example.orderprocessing.application.port.OutboxRepositoryPort;
import com.example.orderprocessing.domain.model.OutboxStatus;
import com.example.orderprocessing.infrastructure.persistence.entity.OutboxEventEntity;
import com.example.orderprocessing.infrastructure.persistence.repository.SpringOutboxJpaRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class OutboxRepositoryAdapter implements OutboxRepositoryPort {

    private final SpringOutboxJpaRepository repository;

    public OutboxRepositoryAdapter(SpringOutboxJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public OutboxEventEntity save(OutboxEventEntity outboxEvent) {
        return repository.save(outboxEvent);
    }

    @Override
    public List<OutboxEventEntity> findPendingForPublish() {
        return repository.findTop50ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(OutboxStatus.PENDING, Instant.now());
    }

    @Override
    public boolean claimForPublish(Long id) {
        return repository.transitionStatus(id, OutboxStatus.PENDING, OutboxStatus.IN_PROGRESS) == 1;
    }
}
