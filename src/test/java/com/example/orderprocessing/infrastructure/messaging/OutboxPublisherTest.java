package com.example.orderprocessing.infrastructure.messaging;

import com.example.orderprocessing.application.port.OutboxRepository;
import com.example.orderprocessing.infrastructure.persistence.entity.OutboxEntity;
import com.example.orderprocessing.infrastructure.persistence.entity.OutboxStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxPublisherTest {

    @Test
    void pollAndPublishProcessesOwnedPartitionBatchesOnly() {
        OutboxRepository repository = mock(OutboxRepository.class);
        OutboxFetcher fetcher = mock(OutboxFetcher.class);
        OutboxProcessor processor = mock(OutboxProcessor.class);
        TransactionTemplate tx = new TransactionTemplate(new NoOpTransactionManager());
        when(repository.countByStatus(OutboxStatus.PENDING)).thenReturn(1L);
        when(repository.countByStatus(OutboxStatus.FAILED)).thenReturn(0L);

        OutboxEntity event = new OutboxEntity();
        event.setAggregateId("order-1");
        event.setCreatedAt(Instant.now());
        when(fetcher.claimPartitionBatch(eq(0), eq(12), eq(100))).thenReturn(List.of(event));
        when(fetcher.claimPartitionBatch(eq(1), eq(12), eq(100))).thenReturn(List.of());
        when(processor.processBatch(List.of(event))).thenReturn(CompletableFuture.completedFuture(null));

        OutboxPublisher publisher = new OutboxPublisher(
                repository,
                fetcher,
                processor,
                tx,
                new SimpleMeterRegistry(),
                12,
                100,
                1,
                1,
                2,
                0,
                1,
                1500,
                7);

        publisher.pollAndPublish();
        publisher.shutdown();

        verify(fetcher).claimPartitionBatch(0, 12, 100);
        verify(processor).processBatch(List.of(event));
        verify(fetcher, never()).claimPartitionBatch(eq(1), any(Integer.class), any(Integer.class));
    }

    @Test
    void cleanupSentEventsArchivesAndDeletesBatch() {
        OutboxRepository repository = mock(OutboxRepository.class);
        OutboxFetcher fetcher = mock(OutboxFetcher.class);
        OutboxProcessor processor = mock(OutboxProcessor.class);
        TransactionTemplate tx = new TransactionTemplate(new NoOpTransactionManager());
        when(repository.countByStatus(OutboxStatus.PENDING)).thenReturn(0L);
        when(repository.countByStatus(OutboxStatus.FAILED)).thenReturn(0L);
        OutboxEntity sent = new OutboxEntity();
        sent.setAggregateId("a");
        when(repository.findSentOlderThan(any())).thenReturn(List.of(sent));

        OutboxPublisher publisher = new OutboxPublisher(
                repository, fetcher, processor, tx, new SimpleMeterRegistry(),
                12, 100, 1, 1, 1, 0, 1, 1500, 7);

        publisher.cleanupSentEvents();
        publisher.shutdown();

        verify(repository).saveArchiveBatch(eq(List.of(sent)), any());
        verify(repository).deleteBatch(List.of(sent));
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
