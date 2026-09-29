package com.vasmarketing.campaign.infrastructure.persistence;

import com.vasmarketing.campaign.domain.model.Campaign;
import com.vasmarketing.campaign.domain.model.CampaignErrors;
import com.vasmarketing.campaign.domain.model.CampaignId;
import com.vasmarketing.campaign.domain.port.CampaignRepository;
import com.vasmarketing.platform.types.DomainRuleViolation;
import com.vasmarketing.platform.types.TenantId;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class JpaCampaignRepository implements CampaignRepository {

  private static final String NAME_CONSTRAINT = "campaigns_tenant_name_uk";

  private final CampaignJpaRepository jpa;

  JpaCampaignRepository(CampaignJpaRepository jpa) {
    this.jpa = jpa;
  }

  /** Maps inside the transaction so lazy collections load before the session closes. */
  @Override
  @Transactional(readOnly = true)
  public Optional<Campaign> findById(TenantId tenantId, CampaignId id) {
    return jpa.findByIdAndTenantId(id.value(), tenantId.value()).map(CampaignMapper::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public boolean existsByName(TenantId tenantId, String name) {
    return jpa.existsByTenantIdAndName(tenantId.value(), name);
  }

  @Override
  @Transactional
  public Campaign save(Campaign campaign) {
    CampaignEntity entity =
        campaign.version().map(v -> managedEntity(campaign, v)).orElseGet(CampaignEntity::new);
    CampaignMapper.apply(campaign, entity);
    try {
      return CampaignMapper.toDomain(jpa.saveAndFlush(entity));
    } catch (DataIntegrityViolationException e) {
      if (violates(e, NAME_CONSTRAINT)) {
        // Lost a race with a concurrent create/rename; same outcome as the pre-check.
        throw new DomainRuleViolation(
            CampaignErrors.DUPLICATE_NAME,
            "a campaign named '" + campaign.details().name() + "' already exists");
      }
      throw e;
    }
  }

  private CampaignEntity managedEntity(Campaign campaign, long expectedVersion) {
    CampaignEntity entity =
        jpa.findByIdAndTenantId(campaign.id().value(), campaign.tenantId().value())
            .orElseThrow(
                () ->
                    new ObjectOptimisticLockingFailureException(
                        CampaignEntity.class, campaign.id().value()));
    if (entity.version != expectedVersion) {
      throw new ObjectOptimisticLockingFailureException(CampaignEntity.class, entity.id);
    }
    return entity;
  }

  private static boolean violates(DataIntegrityViolationException e, String constraint) {
    return e.getMostSpecificCause().getMessage() != null
        && e.getMostSpecificCause().getMessage().contains(constraint);
  }
}
