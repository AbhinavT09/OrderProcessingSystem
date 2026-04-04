package com.example.orderprocessing.infrastructure.persistence.adapter;

import com.example.orderprocessing.application.port.ProcessedEventRepository;
import com.example.orderprocessing.infrastructure.persistence.entity.ProcessedEventEntity;
import com.example.orderprocessing.infrastructure.persistence.repository.SpringProcessedEventJpaRepository;
import org.springframework.stereotype.Component;

@Component
/**
 * JpaProcessedEventRepository implements a concrete responsibility in the order processing service.
 * It is used to keep the boots the Spring runtime for the service layer explicit and maintainable in this architecture.
 */
public class JpaProcessedEventRepository implements ProcessedEventRepository {

    private final SpringProcessedEventJpaRepository repository;

    /**
     * Creates a processed-event repository adapter.
     * @param repository Spring Data repository dependency
     */
    public JpaProcessedEventRepository(SpringProcessedEventJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    /**
     * Executes existsByEventId.
     * @param eventId input argument used by this operation
     * @return operation result
     */
    public boolean existsByEventId(String eventId) {
        return repository.existsByEventId(eventId);
    }

    @Override
    /**
     * Executes save.
     * @param event input argument used by this operation
     * @return operation result
     */
    public ProcessedEventEntity save(ProcessedEventEntity event) {
        return repository.save(event);
    }
}
