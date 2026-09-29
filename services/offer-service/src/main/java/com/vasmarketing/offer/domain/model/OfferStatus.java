package com.vasmarketing.offer.domain.model;

public enum OfferStatus {
  DRAFT,
  ACTIVE,
  PAUSED,
  EXPIRED,
  /** Total budget fully committed; terminal. */
  EXHAUSTED;

  /** Terms and rules may change only while no customer can be receiving the offer. */
  public boolean isEditable() {
    return this == DRAFT || this == PAUSED;
  }
}
