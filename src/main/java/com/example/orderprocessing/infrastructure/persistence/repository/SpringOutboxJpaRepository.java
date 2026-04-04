package com.example.orderprocessing.infrastructure.persistence.repository;

import com.example.orderprocessing.domain.model.OutboxStatus;
import com.example.orderprocessing.infrastructure.persistence.entity.OutboxEventEntity;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpringOutboxJpaRepository extends JpaRepository<OutboxEventEntity, Long> {

    List<OutboxEventEntity> findTop50ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(OutboxStatus status, Instant now);

    @Modifying
    @Query("""
            update OutboxEventEntity e
            set e.status = :nextStatus
            where e.id = :id and e.status = :expectedStatus
            """)
    int transitionStatus(@Param("id") Long id,
                         @Param("expectedStatus") OutboxStatus expectedStatus,
                         @Param("nextStatus") OutboxStatus nextStatus);
}
