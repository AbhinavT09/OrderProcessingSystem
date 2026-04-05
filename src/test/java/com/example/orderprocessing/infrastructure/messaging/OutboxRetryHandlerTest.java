package com.example.orderprocessing.infrastructure.messaging;

import com.example.orderprocessing.application.port.OutboxRepository;
import com.example.orderprocessing.infrastructure.messaging.retry.RetryClassification;
import com.example.orderprocessing.infrastructure.messaging.retry.RetryPolicyStrategy;
import com.example.orderprocessing.infrastructure.persistence.entity.OutboxEntity;
import com.example.orderprocessing.infrastructure.persistence.entity.OutboxStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxRetryHandlerTest {

    @Test
    void handleFailurePersistsRetryMetadataAndSchedulesNextAttempt() {
        OutboxRepository repository = mock(OutboxRepository.class);
        RetryPolicyStrategy retryPolicy = mock(RetryPolicyStrategy.class);
        when(repository.markFailedIfLeased(any(), any(), anyLong(), anyInt(), any(), any(), any())).thenReturn(true);
        when(retryPolicy.plan(any(), anyInt()))
                .thenReturn(new RetryPolicyStrategy.RetryPlan(
                        RetryClassification.TRANSIENT,
                        2500L,
                        "NETWORK",
                        "connection reset"));

        OutboxRetryHandler handler = new OutboxRetryHandler(
                repository,
                new TransactionTemplate(new NoOpTransactionManager()),
                new SimpleMeterRegistry(),
                retryPolicy,
                12);

        OutboxEntity outbox = outboxEntity();
        handler.handleFailure(outbox, new RuntimeException("broker unavailable"));

        assertEquals(1, outbox.getRetryCount());
        assertEquals("NETWORK", outbox.getFailureType());
        assertEquals("connection reset", outbox.getLastFailureReason());
        assertNotNull(outbox.getNextAttemptAt());
        verify(repository, times(1)).markFailedIfLeased(eq(outbox.getId()), eq("lease-a"), eq(0L), eq(1), eq("NETWORK"), eq("connection reset"), any());
    }

    @Test
    void handleFailureTerminalizesPermanentFailureImmediately() {
        OutboxRepository repository = mock(OutboxRepository.class);
        RetryPolicyStrategy retryPolicy = mock(RetryPolicyStrategy.class);
        when(repository.markFailedIfLeased(any(), any(), anyLong(), anyInt(), any(), any(), any())).thenReturn(true);
        when(retryPolicy.plan(any(), anyInt()))
                .thenReturn(new RetryPolicyStrategy.RetryPlan(
                        RetryClassification.PERMANENT,
                        1000L,
                        "VALIDATION",
                        "invalid schema payload"));

        OutboxRetryHandler handler = new OutboxRetryHandler(
                repository,
                new TransactionTemplate(new NoOpTransactionManager()),
                new SimpleMeterRegistry(),
                retryPolicy,
                12);

        OutboxEntity outbox = outboxEntity();
        handler.handleFailure(outbox, new IllegalArgumentException("invalid payload"));

        assertEquals(1, outbox.getRetryCount());
        assertEquals(OutboxStatus.FAILED, outbox.getStatus());
        verify(repository, times(1)).markFailedIfLeased(eq(outbox.getId()), eq("lease-a"), eq(0L), eq(1), eq("VALIDATION"), eq("invalid schema payload"), any());
    }

    private OutboxEntity outboxEntity() {
        OutboxEntity outbox = new OutboxEntity();
        outbox.setId(UUID.randomUUID());
        outbox.setAggregateType("ORDER");
        outbox.setAggregateId("order-1");
        outbox.setEventType("ORDER_CREATED");
        outbox.setPayload("{}");
        outbox.setStatus(OutboxStatus.FAILED);
        outbox.setLeaseOwner("lease-a");
        outbox.setRetryCount(0);
        outbox.setPartitionKey(1);
        outbox.setCreatedAt(Instant.now().minusSeconds(30));
        outbox.setUpdatedAt(Instant.now().minusSeconds(10));
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
