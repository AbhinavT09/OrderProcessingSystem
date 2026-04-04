package com.example.orderprocessing.infrastructure.persistence.repository;

import com.example.orderprocessing.infrastructure.persistence.entity.OutboxEntity;
import com.example.orderprocessing.infrastructure.persistence.entity.OutboxStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data repository for active outbox rows.
 *
 * <p>Supports transactional outbox dispatch with status queries, leasing, and cleanup scans.</p>
 */
public interface SpringOutboxJpaRepository extends JpaRepository<OutboxEntity, UUID> {
    /**
     * Reads oldest events in provided statuses.
     *
     * @param statuses statuses eligible for retrieval
     * @return ordered batch of outbox rows
     */
    List<OutboxEntity> findTop100ByStatusInOrderByCreatedAtAsc(List<OutboxStatus> statuses);
    /**
     * Counts rows by status.
     *
     * @param status lifecycle status
     * @return row count
     */
    long countByStatus(OutboxStatus status);
    /**
     * Checks if a row still exists in expected state.
     *
     * @param id row id
     * @param status expected status
     * @return true when row is present in that state
     */
    boolean existsByIdAndStatus(UUID id, OutboxStatus status);

    @Query(value = """
            SELECT * FROM outbox_events oe
            WHERE oe.partition_key = :partitionKey
              AND oe.status IN ('PENDING', 'FAILED')
              AND oe.next_attempt_at <= :now
              AND oe.retry_count < :maxRetries
            ORDER BY oe.created_at ASC
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEntity> claimBatchForPartition(
            @Param("partitionKey") int partitionKey,
            @Param("now") Instant now,
            @Param("maxRetries") int maxRetries,
            @Param("batchSize") int batchSize);

    /**
     * Retrieves sent rows older than a cutoff for archival.
     *
     * @param status status filter (typically SENT)
     * @param updatedAt upper bound for update timestamp
     * @return archival candidate rows ordered by updated time
     */
    List<OutboxEntity> findTop500ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(OutboxStatus status, Instant updatedAt);
}
