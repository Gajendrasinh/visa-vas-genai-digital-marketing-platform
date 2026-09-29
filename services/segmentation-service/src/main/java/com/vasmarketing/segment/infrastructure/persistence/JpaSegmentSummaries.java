package com.vasmarketing.segment.infrastructure.persistence;

import com.vasmarketing.platform.types.TenantId;
import com.vasmarketing.segment.application.SegmentSummaries;
import com.vasmarketing.segment.application.SegmentSummary;
import com.vasmarketing.segment.domain.model.SegmentStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class JpaSegmentSummaries implements SegmentSummaries {

  private final EntityManager entityManager;

  JpaSegmentSummaries(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  @Override
  public List<SegmentSummary> page(
      TenantId tenantId, SegmentStatus status, UUID before, int fetchSize) {
    StringBuilder jpql =
        new StringBuilder(
            "select new com.vasmarketing.segment.application.SegmentSummary(s.id, s.name, s.status,"
                + " s.origin, s.definitionVersion, s.createdAt, s.version) from SegmentEntity s"
                + " where s.tenantId = :tenant");
    if (status != null) {
      jpql.append(" and s.status = :status");
    }
    if (before != null) {
      jpql.append(" and s.id < :before");
    }
    jpql.append(" order by s.id desc");
    TypedQuery<SegmentSummary> query =
        entityManager
            .createQuery(jpql.toString(), SegmentSummary.class)
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
