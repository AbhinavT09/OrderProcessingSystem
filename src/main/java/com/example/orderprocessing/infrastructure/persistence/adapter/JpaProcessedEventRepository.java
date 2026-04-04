package com.example.orderprocessing.infrastructure.persistence.adapter;

import com.example.orderprocessing.application.port.ProcessedEventRepository;
import com.example.orderprocessing.infrastructure.persistence.entity.ProcessedEventEntity;
import com.example.orderprocessing.infrastructure.persistence.repository.SpringProcessedEventJpaRepository;
import org.springframework.stereotype.Component;

@Component
public class JpaProcessedEventRepository implements ProcessedEventRepository {

    private final SpringProcessedEventJpaRepository repository;

    public JpaProcessedEventRepository(SpringProcessedEventJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean existsByEventId(String eventId) {
        return repository.existsByEventId(eventId);
    }

    @Override
    public ProcessedEventEntity save(ProcessedEventEntity event) {
        return repository.save(event);
    }
}
