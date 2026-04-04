package com.example.orderprocessing.infrastructure.persistence.repository;

import com.example.orderprocessing.infrastructure.persistence.entity.OutboxArchiveEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for archived outbox rows.
 *
 * <p>Persists historical delivery records after they are removed from the active outbox table.</p>
 */
public interface SpringOutboxArchiveJpaRepository extends JpaRepository<OutboxArchiveEntity, UUID> {
}
