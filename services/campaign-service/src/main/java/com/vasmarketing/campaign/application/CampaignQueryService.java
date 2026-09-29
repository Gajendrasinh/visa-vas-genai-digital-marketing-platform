package com.vasmarketing.campaign.application;

import com.vasmarketing.campaign.domain.model.Campaign;
import com.vasmarketing.campaign.domain.model.CampaignId;
import com.vasmarketing.campaign.domain.model.CampaignStatus;
import com.vasmarketing.platform.types.Actor;
import com.vasmarketing.platform.types.CursorPage;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CampaignQueryService {

  private final CampaignLoader loader;
  private final CampaignSummaries summaries;

  CampaignQueryService(CampaignLoader loader, CampaignSummaries summaries) {
    this.loader = loader;
    this.summaries = summaries;
  }

  public Campaign get(Actor actor, CampaignId id) {
    return loader.load(actor, id);
  }

  /** Newest campaigns first; {@code cursor} is the {@code nextCursor} of the previous page. */
  public CursorPage<CampaignSummary> list(
      Actor actor, CampaignStatus status, String cursor, int limit) {
    int size = CursorPage.limit(limit);
    UUID before = cursor == null ? null : CursorPage.decode(cursor);
    return CursorPage.fromOverfetch(
        summaries.page(actor.tenantId(), status, before, size + 1), size, CampaignSummary::id);
  }
}
