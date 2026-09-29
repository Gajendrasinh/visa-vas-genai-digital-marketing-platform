package com.vasmarketing.offer.infrastructure.persistence;

import com.vasmarketing.offer.domain.model.Offer;
import com.vasmarketing.offer.domain.model.OfferErrors;
import com.vasmarketing.offer.domain.model.OfferId;
import com.vasmarketing.offer.domain.port.OfferRepository;
import com.vasmarketing.platform.types.DomainRuleViolation;
import com.vasmarketing.platform.types.TenantId;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class JpaOfferRepository implements OfferRepository {

  private static final String TITLE_CONSTRAINT = "offers_tenant_title_uk";

  private final OfferJpaRepository jpa;

  JpaOfferRepository(OfferJpaRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Offer> findById(TenantId tenantId, OfferId id) {
    return jpa.findByIdAndTenantId(id.value(), tenantId.value()).map(OfferMapper::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public boolean existsByTitle(TenantId tenantId, String title) {
    return jpa.existsByTenantIdAndTitle(tenantId.value(), title);
  }

  @Override
  @Transactional
  public Offer save(Offer offer) {
    OfferEntity entity =
        offer.version().map(v -> managedEntity(offer, v)).orElseGet(OfferEntity::new);
    OfferMapper.apply(offer, entity);
    try {
      return OfferMapper.toDomain(jpa.saveAndFlush(entity));
    } catch (DataIntegrityViolationException e) {
      String cause = e.getMostSpecificCause().getMessage();
      if (cause != null && cause.contains(TITLE_CONSTRAINT)) {
        throw new DomainRuleViolation(
            OfferErrors.DUPLICATE_TITLE,
            "an offer titled '" + offer.terms().title() + "' already exists");
      }
      throw e;
    }
  }

  private OfferEntity managedEntity(Offer offer, long expectedVersion) {
    OfferEntity entity =
        jpa.findByIdAndTenantId(offer.id().value(), offer.tenantId().value())
            .orElseThrow(
                () ->
                    new ObjectOptimisticLockingFailureException(
                        OfferEntity.class, offer.id().value()));
    if (entity.version != expectedVersion) {
      throw new ObjectOptimisticLockingFailureException(OfferEntity.class, entity.id);
    }
    return entity;
  }
}
