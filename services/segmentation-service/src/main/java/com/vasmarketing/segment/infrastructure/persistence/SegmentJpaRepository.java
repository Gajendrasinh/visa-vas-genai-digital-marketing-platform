package com.vasmarketing.segment.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SegmentJpaRepository extends JpaRepository<SegmentEntity, UUID> {

  Optional<SegmentEntity> findByIdAndTenantId(UUID id, UUID tenantId);

  boolean existsByTenantIdAndName(UUID tenantId, String name);
}
