package com.vasmarketing.campaign.application;

import com.vasmarketing.campaign.domain.model.Campaign;
import com.vasmarketing.campaign.domain.model.CampaignId;
import com.vasmarketing.platform.types.Actor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CampaignQueryService {

  private final CampaignLoader loader;

  CampaignQueryService(CampaignLoader loader) {
    this.loader = loader;
  }

  public Campaign get(Actor actor, CampaignId id) {
    return loader.load(actor, id);
  }
}
