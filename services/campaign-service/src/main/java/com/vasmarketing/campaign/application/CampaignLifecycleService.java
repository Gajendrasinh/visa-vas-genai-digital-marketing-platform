package com.vasmarketing.campaign.application;

import com.vasmarketing.campaign.domain.model.Campaign;
import com.vasmarketing.campaign.domain.model.CampaignId;
import com.vasmarketing.campaign.domain.port.CampaignRepository;
import com.vasmarketing.platform.types.Actor;
import java.time.Clock;
import java.time.Instant;
import java.util.function.BiConsumer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Use cases that move a campaign through its lifecycle (submit, approve, publish, ...). */
@Service
@Transactional
public class CampaignLifecycleService {

  private final CampaignRepository repository;
  private final CampaignLoader loader;
  private final Clock clock;

  CampaignLifecycleService(CampaignRepository repository, CampaignLoader loader, Clock clock) {
    this.repository = repository;
    this.loader = loader;
    this.clock = clock;
  }

  public Campaign submit(Actor actor, CampaignId id) {
    return apply(actor, id, (campaign, now) -> campaign.submit(actor, now));
  }

  public Campaign approve(Actor actor, CampaignId id, String comment) {
    return apply(actor, id, (campaign, now) -> campaign.approve(actor, comment, now));
  }

  public Campaign reject(Actor actor, CampaignId id, String reason) {
    return apply(actor, id, (campaign, now) -> campaign.reject(actor, reason, now));
  }

  public Campaign publish(Actor actor, CampaignId id) {
    return apply(actor, id, (campaign, now) -> campaign.publish(actor, now));
  }

  public Campaign pause(Actor actor, CampaignId id) {
    return apply(actor, id, (campaign, now) -> campaign.pause(actor, now));
  }

  public Campaign resume(Actor actor, CampaignId id) {
    return apply(actor, id, (campaign, now) -> campaign.resume(actor, now));
  }

  public Campaign archive(Actor actor, CampaignId id) {
    return apply(actor, id, (campaign, now) -> campaign.archive(actor, now));
  }

  private Campaign apply(Actor actor, CampaignId id, BiConsumer<Campaign, Instant> transition) {
    Campaign campaign = loader.load(actor, id);
    transition.accept(campaign, clock.instant());
    return repository.save(campaign);
  }
}
