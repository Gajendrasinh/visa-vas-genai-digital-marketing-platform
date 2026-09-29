package com.vasmarketing.campaign.application;

import com.vasmarketing.campaign.domain.model.Campaign;
import com.vasmarketing.campaign.domain.model.CampaignId;
import com.vasmarketing.campaign.domain.port.CampaignRepository;
import com.vasmarketing.platform.types.Actor;
import com.vasmarketing.platform.types.Precondition;
import com.vasmarketing.platform.types.ResourceNotFound;
import org.springframework.stereotype.Component;

/** Loads a campaign within the actor's tenant, reporting other tenants' campaigns as absent. */
@Component
class CampaignLoader {

  private final CampaignRepository repository;

  CampaignLoader(CampaignRepository repository) {
    this.repository = repository;
  }

  Campaign load(Actor actor, CampaignId id) {
    return repository
        .findById(actor.tenantId(), id)
        .orElseThrow(() -> new ResourceNotFound("Campaign", id));
  }

  /** Loads for modification, enforcing the client's If-Match precondition. */
  Campaign loadForUpdate(Actor actor, CampaignId id, Precondition precondition) {
    Campaign campaign = load(actor, id);
    precondition.check(campaign.version());
    return campaign;
  }
}
