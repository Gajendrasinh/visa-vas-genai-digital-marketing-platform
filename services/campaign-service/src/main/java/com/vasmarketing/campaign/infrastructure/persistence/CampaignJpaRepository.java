package com.vasmarketing.campaign.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

interface CampaignJpaRepository extends JpaRepository<CampaignEntity, UUID> {

  /**
   * Fetches variants with the campaign. Only one collection is joined: joining channels as well
   * would multiply variant rows (Cartesian product). Channels and approvals load via batch fetch.
   */
  @EntityGraph(attributePaths = "variants")
  Optional<CampaignEntity> findByIdAndTenantId(UUID id, UUID tenantId);

  boolean existsByTenantIdAndName(UUID tenantId, String name);
}
