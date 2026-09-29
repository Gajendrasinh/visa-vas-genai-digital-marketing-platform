package com.vasmarketing.offer.domain.model;

import com.vasmarketing.platform.types.DomainRuleViolation;
import java.time.Instant;
import java.util.Objects;

public record Validity(Instant startAt, Instant endAt) {

  public Validity {
    Objects.requireNonNull(startAt, "startAt");
    Objects.requireNonNull(endAt, "endAt");
    if (!endAt.isAfter(startAt)) {
      throw new DomainRuleViolation(OfferErrors.INVALID_OFFER, "endAt must be after startAt");
    }
  }

  public boolean contains(Instant instant) {
    return !instant.isBefore(startAt) && instant.isBefore(endAt);
  }

  public boolean hasEndedAt(Instant instant) {
    return !instant.isBefore(endAt);
  }
}
