package com.vasmarketing.campaign.domain.port;

import com.vasmarketing.campaign.domain.event.CampaignEvent;
import com.vasmarketing.campaign.domain.model.Campaign;
import java.util.List;

/**
 * Publishes campaign events together with a snapshot of the campaign. Implementations must take
 * part in the caller's transaction (transactional outbox), never publish directly.
 */
public interface CampaignEventPublisher {

  void publish(Campaign campaign, List<CampaignEvent> events);
}
