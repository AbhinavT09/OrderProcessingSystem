package com.example.orderprocessing.application.port;

import com.example.orderprocessing.infrastructure.persistence.entity.ProcessedEventEntity;

public interface ProcessedEventRepository {
    boolean existsByEventId(String eventId);
    ProcessedEventEntity save(ProcessedEventEntity event);
}
