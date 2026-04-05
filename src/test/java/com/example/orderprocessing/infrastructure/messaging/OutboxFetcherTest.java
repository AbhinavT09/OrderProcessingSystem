package com.example.orderprocessing.infrastructure.messaging;

import com.example.orderprocessing.application.port.OutboxRepository;
import com.example.orderprocessing.infrastructure.persistence.entity.OutboxEntity;
import com.example.orderprocessing.infrastructure.persistence.entity.OutboxStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.ArrayList;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxFetcherTest {

    @Test
    void claimPartitionBatchMarksRowsInFlightWithinLease() {
        OutboxRepository outboxRepository = mock(OutboxRepository.class);
        TransactionTemplate tx = new TransactionTemplate(new NoOpTransactionManager());
        OutboxFetcher fetcher = new OutboxFetcher(outboxRepository, tx, new SimpleMeterRegistry(), 30_000L);

        OutboxEntity first = outbox("order-a");
        OutboxEntity second = outbox("order-b");
        List<OutboxEntity> mutableClaimed = new ArrayList<>();
        mutableClaimed.add(first);
        mutableClaimed.add(second);
        when(outboxRepository.claimBatchForPartition(any(Integer.class), any(), any(Integer.class), any(Integer.class)))
                .thenReturn(mutableClaimed);
        when(outboxRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<OutboxEntity> claimed = fetcher.claimPartitionBatch(0, 12, 100);

        assertEquals(2, claimed.size());
        assertTrue(claimed.stream().allMatch(event -> event.getStatus() == OutboxStatus.IN_FLIGHT));
        assertTrue(claimed.stream().allMatch(event -> event.getNextAttemptAt() != null));
        assertTrue(claimed.stream().allMatch(event -> event.getLeaseOwner() != null && !event.getLeaseOwner().isBlank()));
        assertTrue(claimed.stream().allMatch(event -> event.getLeaseVersion() != null && event.getLeaseVersion() > 0L));
        verify(outboxRepository, times(1)).saveAll(claimed);
    }

    private OutboxEntity outbox(String aggregateId) {
        OutboxEntity outbox = new OutboxEntity();
        outbox.setId(UUID.randomUUID());
        outbox.setAggregateType("ORDER");
        outbox.setAggregateId(aggregateId);
        outbox.setEventType("ORDER_CREATED");
        outbox.setPayload("{}");
        outbox.setRetryCount(0);
        outbox.setPartitionKey(0);
        outbox.setStatus(OutboxStatus.PENDING);
        outbox.setCreatedAt(Instant.now().minusSeconds(10));
        outbox.setNextAttemptAt(Instant.now());
        return outbox;
    }

    private static final class NoOpTransactionManager implements PlatformTransactionManager {
        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
        }

        @Override
        public void rollback(TransactionStatus status) {
        }
    }
}
