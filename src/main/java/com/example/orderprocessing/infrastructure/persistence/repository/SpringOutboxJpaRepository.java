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
 * SpringOutboxJpaRepository interface defines a stable boundary used by collaborating components.
 * It is used to keep the boots the Spring runtime for the service layer explicit and maintainable in this architecture.
 */
public interface SpringOutboxJpaRepository extends JpaRepository<OutboxEntity, UUID> {
    /**
     * Performs findTop100ByStatusInOrderByCreatedAtAsc.
     * @param statuses input argument used by this operation
     * @return operation result
     */
    List<OutboxEntity> findTop100ByStatusInOrderByCreatedAtAsc(List<OutboxStatus> statuses);
    /**
     * Performs countByStatus.
     * @param status input argument used by this operation
     * @return operation result
     */
    long countByStatus(OutboxStatus status);
    /**
     * Performs existsByIdAndStatus.
     * @param id input argument used by this operation
     * @param status input argument used by this operation
     * @return operation result
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
     * Performs findTop500ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc.
     * @param status input argument used by this operation
     * @param updatedAt input argument used by this operation
     * @return operation result
     */
    List<OutboxEntity> findTop500ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(OutboxStatus status, Instant updatedAt);
}
