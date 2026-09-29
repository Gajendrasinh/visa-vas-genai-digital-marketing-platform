package com.vasmarketing.offer.domain.port;

import com.vasmarketing.offer.domain.model.Offer;
import com.vasmarketing.offer.domain.model.OfferLifecycleChange;

/** Publishes offer changes through the caller's transaction (transactional outbox). */
public interface OfferEventPublisher {

  void publish(Offer offer, OfferLifecycleChange change);
}
