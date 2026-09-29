package com.vasmarketing.offer.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

interface OfferJpaRepository extends JpaRepository<OfferEntity, UUID> {

  @EntityGraph(attributePaths = "rules")
  Optional<OfferEntity> findByIdAndTenantId(UUID id, UUID tenantId);

  boolean existsByTenantIdAndTitle(UUID tenantId, String title);
}
