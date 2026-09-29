package com.vasmarketing.campaign.domain.model;

import com.vasmarketing.platform.types.DomainRuleViolation;
import java.time.Instant;
import java.util.Objects;

public record Schedule(Instant startAt, Instant endAt) {

  public Schedule {
    Objects.requireNonNull(startAt, "startAt");
    Objects.requireNonNull(endAt, "endAt");
    if (!endAt.isAfter(startAt)) {
      throw new DomainRuleViolation(CampaignErrors.INVALID_SCHEDULE, "endAt must be after startAt");
    }
  }

  public boolean hasEndedAt(Instant now) {
    return !now.isBefore(endAt);
  }

  public boolean hasStartedAt(Instant now) {
    return !now.isBefore(startAt);
  }
}
