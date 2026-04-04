package com.example.orderprocessing.application.port;

import com.example.orderprocessing.infrastructure.persistence.entity.OutboxEntity;
import com.example.orderprocessing.infrastructure.persistence.entity.OutboxStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * OutboxRepository interface defines a stable boundary used by collaborating components.
 * It is used to keep the boots the Spring runtime for the service layer explicit and maintainable in this architecture.
 */
public interface OutboxRepository {
    /**
     * Performs save.
     * @param outboxEvent input argument used by this operation
     * @return operation result
     */
    OutboxEntity save(OutboxEntity outboxEvent);
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
    /**
     * Performs claimBatchForPartition.
     * @param partitionKey input argument used by this operation
     * @param now input argument used by this operation
     * @param maxRetries input argument used by this operation
     * @param batchSize input argument used by this operation
     * @return operation result
     */
    List<OutboxEntity> claimBatchForPartition(int partitionKey, Instant now, int maxRetries, int batchSize);
    /**
     * Performs findSentOlderThan.
     * @param cutoff input argument used by this operation
     * @return operation result
     */
    List<OutboxEntity> findSentOlderThan(Instant cutoff);
    /**
     * Performs saveArchiveBatch.
     * @param sentEventsToArchive input argument used by this operation
     * @param archivedAt input argument used by this operation
     */
    void saveArchiveBatch(List<OutboxEntity> sentEventsToArchive, Instant archivedAt);
    /**
     * Performs deleteBatch.
     * @param sentEventsToDelete input argument used by this operation
     */
    void deleteBatch(List<OutboxEntity> sentEventsToDelete);
}
