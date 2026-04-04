package com.example.orderprocessing.infrastructure.persistence.adapter;

import com.example.orderprocessing.application.port.ProcessedEventRepository;
import com.example.orderprocessing.infrastructure.persistence.entity.ProcessedEventEntity;
import com.example.orderprocessing.infrastructure.persistence.repository.SpringProcessedEventJpaRepository;
import org.springframework.stereotype.Component;

@Component
/**
 * Infrastructure adapter for processed-event dedup persistence.
 *
 * <p>Backs consumer idempotency by storing event processing markers and checking event ids
 * before applying state transitions.</p>
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
     * Checks whether an event id has already been processed.
     *
     * @param eventId integration event identifier
     * @return true when an existing marker is present
     */
    public boolean existsByEventId(String eventId) {
        return repository.existsByEventId(eventId);
    }

    @Override
    /**
     * Persists a processed-event marker.
     *
     * @param event processed event marker
     * @return persisted marker entity
     */
    public ProcessedEventEntity save(ProcessedEventEntity event) {
        return repository.save(event);
    }
}
