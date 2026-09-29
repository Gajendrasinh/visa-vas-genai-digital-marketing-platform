package com.vasmarketing.offer.domain.model;

/** What happened to an offer; published on offer.events. */
public enum OfferLifecycleChange {
  CREATED,
  TERMS_UPDATED,
  RULES_REPLACED,
  ACTIVATED,
  PAUSED
}
