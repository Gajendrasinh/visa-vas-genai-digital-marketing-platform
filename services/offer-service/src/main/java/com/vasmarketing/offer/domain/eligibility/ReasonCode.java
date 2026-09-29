package com.vasmarketing.offer.domain.eligibility;

/** Machine-readable reasons a customer is not eligible; persisted with every decision. */
public enum ReasonCode {
  OFFER_NOT_ACTIVE,
  OUTSIDE_VALIDITY,
  BUDGET_EXHAUSTED,
  CUSTOMER_CAP_REACHED,
  NOT_IN_TARGET_SEGMENT,
  COUNTRY_NOT_ELIGIBLE,
  INSUFFICIENT_ACTIVITY,
  NO_MARKETING_CONSENT,
  LOW_CATEGORY_AFFINITY
}
