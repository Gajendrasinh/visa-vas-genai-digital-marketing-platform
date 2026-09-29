package com.vasmarketing.campaign.domain.port;

import com.vasmarketing.campaign.domain.model.Campaign;
import com.vasmarketing.campaign.domain.model.CampaignId;
import com.vasmarketing.platform.types.TenantId;
import java.util.Optional;

/** Persistence port for the campaign aggregate. All lookups are tenant-scoped. */
public interface CampaignRepository {

  Optional<Campaign> findById(TenantId tenantId, CampaignId id);

  boolean existsByName(TenantId tenantId, String name);

  /**
   * Inserts or updates the aggregate and returns it as persisted (with its new version). Fails with
   * a concurrency conflict if the stored version differs from the aggregate's version.
   */
  Campaign save(Campaign campaign);
}
