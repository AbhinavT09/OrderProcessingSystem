package com.example.orderprocessing.infrastructure.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.orderprocessing.application.exception.InfrastructureException;
import com.example.orderprocessing.application.port.OrderEventPublisherPort;
import com.example.orderprocessing.application.port.OutboxRepositoryPort;
import com.example.orderprocessing.domain.model.OutboxStatus;
import com.example.orderprocessing.infrastructure.persistence.entity.OutboxEventEntity;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OutboxDispatcherTest {

    @Test
    void dispatchFailureSchedulesRetryAndEventuallyFails() {
        FakeOutboxRepository outboxRepository = new FakeOutboxRepository();
        FailingPublisher publisher = new FailingPublisher();

        OutboxDispatcher dispatcher = new OutboxDispatcher(
                outboxRepository,
                publisher,
                ObservationRegistry.NOOP,
                new SimpleMeterRegistry(),
                2);

        OutboxEventEntity event = new OutboxEventEntity();
        event.setId(1L);
        event.setEventId(UUID.randomUUID());
        event.setAggregateId("agg-1");
        event.setEventType("ORDER_CREATED");
        event.setPayload("{}");
        event.setStatus(OutboxStatus.IN_PROGRESS);
        event.setAttempts(0);
        event.setMaxAttempts(2);
        event.setNextAttemptAt(Instant.now());
        event.setCreatedAt(Instant.now());

        dispatcher.dispatchSingle(event);
        assertEquals(OutboxStatus.PENDING, event.getStatus());
        assertEquals(1, event.getAttempts());

        dispatcher.dispatchSingle(event);
        assertEquals(OutboxStatus.FAILED, event.getStatus());
        assertEquals(2, event.getAttempts());

        assertTrue(outboxRepository.saved.size() >= 2);
    }

    @Test
    void backoffIsCapped() {
        OutboxDispatcher dispatcher = new OutboxDispatcher(
                new FakeOutboxRepository(),
                new FailingPublisher(),
                ObservationRegistry.NOOP,
                new SimpleMeterRegistry(),
                10);

        long backoff = dispatcher.computeBackoffSeconds(8);
        assertTrue(backoff <= 300L);
    }

    static class FakeOutboxRepository implements OutboxRepositoryPort {
        final List<OutboxEventEntity> saved = new ArrayList<>();

        @Override
        public OutboxEventEntity save(OutboxEventEntity outboxEvent) {
            saved.add(outboxEvent);
            return outboxEvent;
        }

        @Override
        public List<OutboxEventEntity> findPendingForPublish() {
            return List.of();
        }

        @Override
        public boolean claimForPublish(Long id) {
            return true;
        }
    }

    static class FailingPublisher implements OrderEventPublisherPort {
        @Override
        public void publish(OutboxEventEntity event) {
            throw new InfrastructureException("kafka down", new RuntimeException("kafka down"));
        }
    }
}
