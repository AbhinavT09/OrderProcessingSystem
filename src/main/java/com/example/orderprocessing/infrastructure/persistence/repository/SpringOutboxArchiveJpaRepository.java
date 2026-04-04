package com.example.orderprocessing.infrastructure.persistence.repository;

import com.example.orderprocessing.infrastructure.persistence.entity.OutboxArchiveEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringOutboxArchiveJpaRepository extends JpaRepository<OutboxArchiveEntity, UUID> {
}
