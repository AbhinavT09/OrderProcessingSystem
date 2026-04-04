package com.example.orderprocessing.infrastructure.persistence.repository;

import com.example.orderprocessing.infrastructure.persistence.entity.OutboxEntity;
import com.example.orderprocessing.infrastructure.persistence.entity.OutboxStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpringOutboxJpaRepository extends JpaRepository<OutboxEntity, UUID> {
    List<OutboxEntity> findTop100ByStatusInOrderByCreatedAtAsc(List<OutboxStatus> statuses);
    long countByStatus(OutboxStatus status);
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

    List<OutboxEntity> findTop500ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(OutboxStatus status, Instant updatedAt);
}
