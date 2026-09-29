package com.vasmarketing.offer.infrastructure.persistence;

import com.vasmarketing.offer.application.OfferSummaries;
import com.vasmarketing.offer.application.OfferSummary;
import com.vasmarketing.offer.domain.model.OfferCategory;
import com.vasmarketing.offer.domain.model.OfferStatus;
import com.vasmarketing.platform.types.TenantId;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** Keyset pagination with a constructor projection; the JPQL is assembled from fixed fragments. */
@Repository
class JpaOfferSummaries implements OfferSummaries {

  private final EntityManager entityManager;

  JpaOfferSummaries(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  @Override
  public List<OfferSummary> page(
      TenantId tenantId, OfferStatus status, OfferCategory category, UUID before, int fetchSize) {
    StringBuilder jpql =
        new StringBuilder(
            "select new com.vasmarketing.offer.application.OfferSummary(o.id, o.merchantId, o.title,"
                + " o.category, o.status, o.startAt, o.endAt, o.version) from OfferEntity o"
                + " where o.tenantId = :tenant");
    if (status != null) {
      jpql.append(" and o.status = :status");
    }
    if (category != null) {
      jpql.append(" and o.category = :category");
    }
    if (before != null) {
      jpql.append(" and o.id < :before");
    }
    jpql.append(" order by o.id desc");
    TypedQuery<OfferSummary> query =
        entityManager
            .createQuery(jpql.toString(), OfferSummary.class)
            .setParameter("tenant", tenantId.value())
            .setMaxResults(fetchSize);
    if (status != null) {
      query.setParameter("status", status);
    }
    if (category != null) {
      query.setParameter("category", category);
    }
    if (before != null) {
      query.setParameter("before", before);
    }
    return query.getResultList();
  }
}
