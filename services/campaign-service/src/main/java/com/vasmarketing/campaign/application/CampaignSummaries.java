package com.vasmarketing.campaign.application;

import com.vasmarketing.campaign.domain.model.CampaignStatus;
import com.vasmarketing.platform.types.TenantId;
import java.util.List;
import java.util.UUID;

/** Read-model port for campaign listings (newest first, keyset-paginated). */
public interface CampaignSummaries {

  /**
   * @param status optional filter, null for all
   * @param before exclusive upper bound on id (the cursor), null for the first page
   * @param fetchSize rows to fetch (callers pass limit + 1 to detect a next page)
   */
  List<CampaignSummary> page(TenantId tenantId, CampaignStatus status, UUID before, int fetchSize);
}
