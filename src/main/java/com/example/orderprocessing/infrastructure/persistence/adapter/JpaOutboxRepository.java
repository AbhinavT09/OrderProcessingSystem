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
 * Infrastructure persistence adapter for the outbox port.
 *
 * <p>Bridges application-level outbox operations to Spring Data JPA repositories, including
 * active-row leasing and archive-table writes used by the transactional outbox pattern.</p>
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
     * Persists or updates an active outbox row.
     *
     * @param outboxEvent outbox event state to persist
     * @return persisted entity
     */
    public OutboxEntity save(OutboxEntity outboxEvent) {
        return repository.save(outboxEvent);
    }

    @Override
    /**
     * Reads a small ordered batch by status for compatibility reads.
     *
     * @param statuses statuses eligible for fetch
     * @return oldest matching events
     */
    public List<OutboxEntity> findTop100ByStatusInOrderByCreatedAtAsc(List<OutboxStatus> statuses) {
        return repository.findTop100ByStatusInOrderByCreatedAtAsc(statuses);
    }

    @Override
    /**
     * Counts active outbox rows by status.
     *
     * @param status status to count
     * @return number of rows in that status
     */
    public long countByStatus(OutboxStatus status) {
        return repository.countByStatus(status);
    }

    @Override
    /**
     * Verifies that a row still matches expected id and status.
     *
     * @param id row id
     * @param status expected status
     * @return true when the row exists in the given state
     */
    public boolean existsByIdAndStatus(UUID id, OutboxStatus status) {
        return repository.existsByIdAndStatus(id, status);
    }

    @Override
    /**
     * Claims due events for a partition using skip-locked semantics.
     *
     * @param partitionKey partition assigned to current publisher worker
     * @param now claim timestamp for due filtering
     * @param maxRetries retry ceiling
     * @param batchSize upper bound of claimed rows
     * @return leased outbox events
     */
    public List<OutboxEntity> claimBatchForPartition(int partitionKey, Instant now, int maxRetries, int batchSize) {
        return repository.claimBatchForPartition(partitionKey, now, maxRetries, batchSize);
    }

    @Override
    /**
     * Finds sent events that are older than the archive cutoff.
     *
     * @param cutoff archival threshold
     * @return events eligible for archive/purge
     */
    public List<OutboxEntity> findSentOlderThan(Instant cutoff) {
        return repository.findTop500ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(OutboxStatus.SENT, cutoff);
    }

    @Override
    /**
     * Copies sent events into archive storage.
     *
     * @param sentEventsToArchive sent rows to archive
     * @param archivedAt archive timestamp
     */
    public void saveArchiveBatch(List<OutboxEntity> sentEventsToArchive, Instant archivedAt) {
        List<OutboxArchiveEntity> archives = sentEventsToArchive.stream()
                .map(event -> OutboxArchiveEntity.from(event, archivedAt))
                .toList();
        archiveRepository.saveAll(archives);
    }

    @Override
    /**
     * Removes events from active outbox table after archive succeeds.
     *
     * @param sentEventsToDelete rows to delete
     */
    public void deleteBatch(List<OutboxEntity> sentEventsToDelete) {
        repository.deleteAllInBatch(sentEventsToDelete);
    }
}
