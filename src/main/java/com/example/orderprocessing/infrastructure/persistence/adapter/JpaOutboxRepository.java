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
/**
 * JpaOutboxRepository implements a concrete responsibility in the order processing service.
 * It is used to keep the boots the Spring runtime for the service layer explicit and maintainable in this architecture.
 */
public class JpaOutboxRepository implements OutboxRepository {

    private final SpringOutboxJpaRepository repository;
    private final SpringOutboxArchiveJpaRepository archiveRepository;

    /**
     * Creates a JPA-backed outbox repository adapter.
     * @param repository active outbox Spring Data repository
     * @param archiveRepository archive outbox Spring Data repository
     */
    public JpaOutboxRepository(SpringOutboxJpaRepository repository,
                              SpringOutboxArchiveJpaRepository archiveRepository) {
        this.repository = repository;
        this.archiveRepository = archiveRepository;
    }

    @Override
    /**
     * Executes save.
     * @param outboxEvent input argument used by this operation
     * @return operation result
     */
    public OutboxEntity save(OutboxEntity outboxEvent) {
        return repository.save(outboxEvent);
    }

    @Override
    /**
     * Executes findTop100ByStatusInOrderByCreatedAtAsc.
     * @param statuses input argument used by this operation
     * @return operation result
     */
    public List<OutboxEntity> findTop100ByStatusInOrderByCreatedAtAsc(List<OutboxStatus> statuses) {
        return repository.findTop100ByStatusInOrderByCreatedAtAsc(statuses);
    }

    @Override
    /**
     * Executes countByStatus.
     * @param status input argument used by this operation
     * @return operation result
     */
    public long countByStatus(OutboxStatus status) {
        return repository.countByStatus(status);
    }

    @Override
    /**
     * Executes existsByIdAndStatus.
     * @param id input argument used by this operation
     * @param status input argument used by this operation
     * @return operation result
     */
    public boolean existsByIdAndStatus(UUID id, OutboxStatus status) {
        return repository.existsByIdAndStatus(id, status);
    }

    @Override
    /**
     * Executes claimBatchForPartition.
     * @param partitionKey input argument used by this operation
     * @param now input argument used by this operation
     * @param maxRetries input argument used by this operation
     * @param batchSize input argument used by this operation
     * @return operation result
     */
    public List<OutboxEntity> claimBatchForPartition(int partitionKey, Instant now, int maxRetries, int batchSize) {
        return repository.claimBatchForPartition(partitionKey, now, maxRetries, batchSize);
    }

    @Override
    /**
     * Executes findSentOlderThan.
     * @param cutoff input argument used by this operation
     * @return operation result
     */
    public List<OutboxEntity> findSentOlderThan(Instant cutoff) {
        return repository.findTop500ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(OutboxStatus.SENT, cutoff);
    }

    @Override
    /**
     * Executes saveArchiveBatch.
     * @param sentEventsToArchive input argument used by this operation
     * @param archivedAt input argument used by this operation
     */
    public void saveArchiveBatch(List<OutboxEntity> sentEventsToArchive, Instant archivedAt) {
        List<OutboxArchiveEntity> archives = sentEventsToArchive.stream()
                .map(event -> OutboxArchiveEntity.from(event, archivedAt))
                .toList();
        archiveRepository.saveAll(archives);
    }

    @Override
    /**
     * Executes deleteBatch.
     * @param sentEventsToDelete input argument used by this operation
     */
    public void deleteBatch(List<OutboxEntity> sentEventsToDelete) {
        repository.deleteAllInBatch(sentEventsToDelete);
    }
}
