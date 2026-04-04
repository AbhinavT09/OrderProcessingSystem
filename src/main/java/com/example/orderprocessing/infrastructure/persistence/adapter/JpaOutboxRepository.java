package com.example.orderprocessing.infrastructure.persistence.adapter;

import com.example.orderprocessing.application.port.OutboxRepository;
import com.example.orderprocessing.infrastructure.persistence.entity.OutboxArchiveEntity;
import com.example.orderprocessing.infrastructure.persistence.entity.OutboxEntity;
import com.example.orderprocessing.infrastructure.persistence.entity.OutboxStatus;
import com.example.orderprocessing.infrastructure.persistence.repository.SpringOutboxArchiveJpaRepository;
import com.example.orderprocessing.infrastructure.persistence.repository.SpringOutboxJpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class JpaOutboxRepository implements OutboxRepository {

    private final SpringOutboxJpaRepository repository;
    private final SpringOutboxArchiveJpaRepository archiveRepository;

    public JpaOutboxRepository(SpringOutboxJpaRepository repository,
                              SpringOutboxArchiveJpaRepository archiveRepository) {
        this.repository = repository;
        this.archiveRepository = archiveRepository;
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

    @Override
    public List<OutboxEntity> claimBatchForPartition(int partitionKey, Instant now, int maxRetries, int batchSize) {
        return repository.claimBatchForPartition(partitionKey, now, maxRetries, batchSize);
    }

    @Override
    public List<OutboxEntity> findSentOlderThan(Instant cutoff) {
        return repository.findTop500ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(OutboxStatus.SENT, cutoff);
    }

    @Override
    public void saveArchiveBatch(List<OutboxEntity> sentEventsToArchive, Instant archivedAt) {
        List<OutboxArchiveEntity> archives = sentEventsToArchive.stream()
                .map(event -> OutboxArchiveEntity.from(event, archivedAt))
                .toList();
        archiveRepository.saveAll(archives);
    }

    @Override
    public void deleteBatch(List<OutboxEntity> sentEventsToDelete) {
        repository.deleteAllInBatch(sentEventsToDelete);
    }
}
