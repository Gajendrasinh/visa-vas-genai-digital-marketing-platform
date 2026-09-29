package com.vasmarketing.campaign.application;

import com.vasmarketing.campaign.domain.model.CampaignDetails;
import com.vasmarketing.campaign.domain.model.CampaignOrigin;
import java.util.Objects;
import java.util.UUID;

/**
 * @param aiRequestId the ai-gateway request that drafted the campaign; required for AI_ASSISTED
 */
public record CreateCampaignCommand(
    CampaignDetails details, CampaignOrigin origin, UUID aiRequestId) {

  public CreateCampaignCommand {
    Objects.requireNonNull(details, "details");
    Objects.requireNonNull(origin, "origin");
  }

  public static CreateCampaignCommand manual(CampaignDetails details) {
    return new CreateCampaignCommand(details, CampaignOrigin.MANUAL, null);
  }
}
