package com.example.orderprocessing.infrastructure.persistence.repository;

import com.example.orderprocessing.infrastructure.persistence.entity.OutboxArchiveEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * SpringOutboxArchiveJpaRepository interface defines a stable boundary used by collaborating components.
 * It is used to keep the boots the Spring runtime for the service layer explicit and maintainable in this architecture.
 */
public interface SpringOutboxArchiveJpaRepository extends JpaRepository<OutboxArchiveEntity, UUID> {
}
