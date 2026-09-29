package com.vasmarketing.offer.domain.port;

import com.vasmarketing.offer.domain.model.Offer;
import com.vasmarketing.offer.domain.model.OfferId;
import com.vasmarketing.platform.types.TenantId;
import java.util.Optional;

public interface OfferRepository {

  Optional<Offer> findById(TenantId tenantId, OfferId id);

  boolean existsByTitle(TenantId tenantId, String title);

  /** Inserts or updates; fails with a concurrency conflict on a stale version. */
  Offer save(Offer offer);
}
