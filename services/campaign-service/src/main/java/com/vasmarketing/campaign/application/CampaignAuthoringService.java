package com.vasmarketing.campaign.application;

import com.vasmarketing.campaign.domain.model.Campaign;
import com.vasmarketing.campaign.domain.model.CampaignDetails;
import com.vasmarketing.campaign.domain.model.CampaignErrors;
import com.vasmarketing.campaign.domain.model.CampaignId;
import com.vasmarketing.campaign.domain.model.ContentAuthor;
import com.vasmarketing.campaign.domain.model.PromptReference;
import com.vasmarketing.campaign.domain.model.VariantContent;
import com.vasmarketing.campaign.domain.model.VariantId;
import com.vasmarketing.campaign.domain.port.CampaignRepository;
import com.vasmarketing.platform.types.Actor;
import com.vasmarketing.platform.types.DomainRuleViolation;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Use cases for creating and editing DRAFT campaigns and their content. */
@Service
@Transactional
public class CampaignAuthoringService {

  private final CampaignRepository repository;
  private final CampaignLoader loader;
  private final Clock clock;

  CampaignAuthoringService(CampaignRepository repository, CampaignLoader loader, Clock clock) {
    this.repository = repository;
    this.loader = loader;
    this.clock = clock;
  }

  public Campaign create(Actor actor, CreateCampaignCommand command) {
    requireUniqueName(actor, command.details().name());
    Campaign campaign =
        Campaign.create(
            actor, command.details(), command.origin(), command.aiRequestId(), clock.instant());
    return repository.save(campaign);
  }

  public Campaign updateDetails(Actor actor, CampaignId id, CampaignDetails details) {
    Campaign campaign = loader.load(actor, id);
    if (!campaign.details().name().equals(details.name())) {
      requireUniqueName(actor, details.name());
    }
    campaign.updateDetails(details, clock.instant());
    return repository.save(campaign);
  }

  public VariantId addVariant(
      Actor actor,
      CampaignId id,
      VariantContent content,
      ContentAuthor author,
      PromptReference prompt) {
    Campaign campaign = loader.load(actor, id);
    VariantId variantId = campaign.addVariant(content, author, prompt, clock.instant());
    repository.save(campaign);
    return variantId;
  }

  public Campaign removeVariant(Actor actor, CampaignId id, VariantId variantId) {
    Campaign campaign = loader.load(actor, id);
    campaign.removeVariant(variantId, clock.instant());
    return repository.save(campaign);
  }

  public Campaign recordCompliance(
      Actor actor, CampaignId id, VariantId variantId, boolean passed, String report) {
    Campaign campaign = loader.load(actor, id);
    campaign.recordCompliance(variantId, passed, report, clock.instant());
    return repository.save(campaign);
  }

  private void requireUniqueName(Actor actor, String name) {
    if (repository.existsByName(actor.tenantId(), name)) {
      throw new DomainRuleViolation(
          CampaignErrors.DUPLICATE_NAME, "a campaign named '" + name + "' already exists");
    }
  }
}
