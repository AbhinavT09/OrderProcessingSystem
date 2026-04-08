package com.example.orderprocessing.infrastructure.persistence.jpa;

import com.example.orderprocessing.infrastructure.persistence.entity.ProcessedEventEntity;
import com.example.orderprocessing.infrastructure.persistence.repository.SpringProcessedEventJpaRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JPA slice: {@code processed_events} uniqueness and dedupe lookup.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class SpringProcessedEventJpaRepositoryDataJpaTest {

    @Autowired
    private SpringProcessedEventJpaRepository repository;

    @Test
    void existsByEventId_reflectsPersistedMarker() {
        ProcessedEventEntity e = new ProcessedEventEntity();
        e.setEventId("evt-dedupe-1");
        e.setEventType("ORDER_CREATED");
        e.setProcessedAt(Instant.now());
        repository.save(e);

        assertTrue(repository.existsByEventId("evt-dedupe-1"));
    }

    @Test
    void duplicateEventIdViolatesUniqueConstraint() {
        ProcessedEventEntity a = new ProcessedEventEntity();
        a.setEventId("evt-dup");
        a.setEventType("ORDER_CREATED");
        a.setProcessedAt(Instant.now());
        repository.save(a);

        ProcessedEventEntity b = new ProcessedEventEntity();
        b.setEventId("evt-dup");
        b.setEventType("ORDER_CREATED");
        b.setProcessedAt(Instant.now());

        assertThrows(DataIntegrityViolationException.class, () -> repository.saveAndFlush(b));
    }
}
