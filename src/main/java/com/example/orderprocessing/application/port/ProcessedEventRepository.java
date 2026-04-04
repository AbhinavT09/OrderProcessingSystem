package com.example.orderprocessing.application.port;

import com.example.orderprocessing.infrastructure.persistence.entity.ProcessedEventEntity;

/**
 * ProcessedEventRepository interface defines a stable boundary used by collaborating components.
 * It is used to keep the boots the Spring runtime for the service layer explicit and maintainable in this architecture.
 */
public interface ProcessedEventRepository {
    /**
     * Performs existsByEventId.
     * @param eventId input argument used by this operation
     * @return operation result
     */
    boolean existsByEventId(String eventId);
    /**
     * Performs save.
     * @param event input argument used by this operation
     * @return operation result
     */
    ProcessedEventEntity save(ProcessedEventEntity event);
}
