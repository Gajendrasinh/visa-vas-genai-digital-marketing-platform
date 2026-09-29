package com.vasmarketing.campaign.infrastructure.persistence;

import com.vasmarketing.campaign.application.CampaignSummaries;
import com.vasmarketing.campaign.application.CampaignSummary;
import com.vasmarketing.campaign.domain.model.CampaignStatus;
import com.vasmarketing.platform.types.TenantId;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * Keyset pagination over (tenant_id, id) with a constructor projection: one query per page, no
 * child collections, and an index-backed range scan regardless of page depth.
 */
@Repository
class JpaCampaignSummaries implements CampaignSummaries {

  private final EntityManager entityManager;

  JpaCampaignSummaries(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  @Override
  public List<CampaignSummary> page(
      TenantId tenantId, CampaignStatus status, UUID before, int fetchSize) {
    StringBuilder jpql =
        new StringBuilder(
            "select new com.vasmarketing.campaign.application.CampaignSummary("
                + "c.id, c.name, c.status, c.segmentId, c.offerId, c.startAt, c.endAt, c.origin,"
                + " c.createdAt, c.version) from CampaignEntity c where c.tenantId = :tenant");
    if (status != null) {
      jpql.append(" and c.status = :status");
    }
    if (before != null) {
      jpql.append(" and c.id < :before");
    }
    jpql.append(" order by c.id desc");
    TypedQuery<CampaignSummary> query =
        entityManager
            .createQuery(jpql.toString(), CampaignSummary.class)
            .setParameter("tenant", tenantId.value())
            .setMaxResults(fetchSize);
    if (status != null) {
      query.setParameter("status", status);
    }
    if (before != null) {
      query.setParameter("before", before);
    }
    return query.getResultList();
  }
}
