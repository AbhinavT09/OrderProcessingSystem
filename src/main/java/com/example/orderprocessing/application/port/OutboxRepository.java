package com.example.orderprocessing.application.port;

import com.example.orderprocessing.infrastructure.persistence.entity.OutboxEntity;
import com.example.orderprocessing.infrastructure.persistence.entity.OutboxStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Hexagonal application port for transactional outbox persistence and lifecycle transitions.
 *
 * <p>Supports leasing, retry orchestration, and archival operations needed by the outbox pattern.</p>
 *
 * <p><b>Lease fencing:</b> state transitions from {@code IN_FLIGHT} use
 * {@code leaseOwner + leaseVersion} to prevent stale workers from overwriting newer claims.</p>
 *
 * <p><b>Transactional context:</b> methods are called from DB transaction templates in outbox
 * workers; Kafka transactions are handled by event-publisher adapters.</p>
 */
public interface OutboxRepository {
    /**
     * Persists or updates an outbox record.
     *
     * @param outboxEvent outbox event entity
     * @return persisted entity
     */
    OutboxEntity save(OutboxEntity outboxEvent);
    /**
     * Persists a batch of outbox records in one call.
     *
     * @param outboxEvents outbox event entities
     * @return persisted entities
     */
    List<OutboxEntity> saveAll(List<OutboxEntity> outboxEvents);
    /**
     * Reads a small ordered batch by status for compatibility flows.
     *
     * @param statuses statuses eligible for fetch
     * @return oldest matching rows
     */
    List<OutboxEntity> findTop100ByStatusInOrderByCreatedAtAsc(List<OutboxStatus> statuses);
    /**
     * Counts events in a specific lifecycle status.
     *
     * @param status outbox status
     * @return number of rows in given status
     */
    long countByStatus(OutboxStatus status);
    /**
     * Checks whether a row still matches expected id/status combination.
     *
     * @param id outbox row id
     * @param status expected status
     * @return true when row exists with that status
     */
    boolean existsByIdAndStatus(UUID id, OutboxStatus status);
    /**
     * Completes an in-flight row only when lease ownership still matches.
     *
     * @param id outbox row id
     * @param leaseOwner current worker lease token
     * @param nextAttemptAt timestamp to persist with status transition
     * @return true when state transition was applied
     */
    boolean markSentIfLeased(UUID id, String leaseOwner, long leaseVersion, Instant nextAttemptAt);
    /**
     * Marks an in-flight row as failed only when lease ownership still matches.
     *
     * @param id outbox row id
     * @param leaseOwner current worker lease token
     * @param retryCount updated retry count
     * @param failureType normalized failure type
     * @param failureReason trimmed failure reason for diagnostics
     * @param nextAttemptAt retry timestamp
     * @return true when state transition was applied
     */
    boolean markFailedIfLeased(UUID id,
                               String leaseOwner,
                               long leaseVersion,
                               int retryCount,
                               String failureType,
                               String failureReason,
                               Instant nextAttemptAt);
    /**
     * Claims due events for one partition with retry cap and size bound.
     *
     * @param partitionKey partition owned by current publisher worker
     * @param now claim timestamp for due filtering
     * @param maxRetries retry ceiling for eligible rows
     * @param batchSize upper bound of claimed rows
     * @return leased rows for processing
     */
    List<OutboxEntity> claimBatchForPartition(int partitionKey, Instant now, int maxRetries, int batchSize);
    /**
     * Finds sent rows older than archival cutoff.
     *
     * @param cutoff archival threshold
     * @return rows eligible for archive/purge
     */
    List<OutboxEntity> findSentOlderThan(Instant cutoff);
    /**
     * Archives sent rows before deletion from active outbox table.
     *
     * @param sentEventsToArchive rows to archive
     * @param archivedAt archive timestamp
     */
    void saveArchiveBatch(List<OutboxEntity> sentEventsToArchive, Instant archivedAt);
    /**
     * Deletes archived rows from active outbox table.
     *
     * @param sentEventsToDelete rows to delete
     */
    void deleteBatch(List<OutboxEntity> sentEventsToDelete);
}
