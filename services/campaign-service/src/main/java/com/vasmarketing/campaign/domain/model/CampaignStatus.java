package com.vasmarketing.campaign.domain.model;

import java.util.EnumSet;
import java.util.Set;

/** Campaign lifecycle states and the only transitions allowed between them (data-model.md §6). */
public enum CampaignStatus {
  DRAFT,
  PENDING_APPROVAL,
  APPROVED,
  SCHEDULED,
  ACTIVE,
  PAUSED,
  COMPLETED,
  ARCHIVED;

  private Set<CampaignStatus> next;

  static {
    DRAFT.next = EnumSet.of(PENDING_APPROVAL, ARCHIVED);
    PENDING_APPROVAL.next = EnumSet.of(APPROVED, DRAFT);
    APPROVED.next = EnumSet.of(SCHEDULED, ACTIVE);
    SCHEDULED.next = EnumSet.of(ACTIVE);
    ACTIVE.next = EnumSet.of(PAUSED, COMPLETED);
    PAUSED.next = EnumSet.of(ACTIVE, COMPLETED);
    COMPLETED.next = EnumSet.of(ARCHIVED);
    ARCHIVED.next = EnumSet.noneOf(CampaignStatus.class);
  }

  public boolean canTransitionTo(CampaignStatus target) {
    return next.contains(target);
  }

  /** Customers may receive messages only in this state. */
  public boolean isLive() {
    return this == ACTIVE;
  }
}
