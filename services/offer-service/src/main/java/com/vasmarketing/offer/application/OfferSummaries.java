package com.vasmarketing.offer.application;

import com.vasmarketing.offer.domain.model.OfferCategory;
import com.vasmarketing.offer.domain.model.OfferStatus;
import com.vasmarketing.platform.types.TenantId;
import java.util.List;
import java.util.UUID;

/** Read-model port for offer listings (newest first, keyset-paginated, optional filters). */
public interface OfferSummaries {

  List<OfferSummary> page(
      TenantId tenantId, OfferStatus status, OfferCategory category, UUID before, int fetchSize);
}
