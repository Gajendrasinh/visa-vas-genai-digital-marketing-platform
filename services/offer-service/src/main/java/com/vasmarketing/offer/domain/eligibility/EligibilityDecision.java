package com.vasmarketing.offer.domain.eligibility;

import com.vasmarketing.offer.domain.model.OfferId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The outcome of an eligibility check, with every failed reason (not just the first) so that the
 * decision is fully explainable and auditable.
 */
public record EligibilityDecision(
    OfferId offerId,
    UUID customerId,
    boolean eligible,
    List<ReasonCode> reasons,
    int ruleSetVersion,
    Instant decidedAt) {

  public EligibilityDecision {
    Objects.requireNonNull(offerId, "offerId");
    Objects.requireNonNull(customerId, "customerId");
    reasons = List.copyOf(reasons);
    Objects.requireNonNull(decidedAt, "decidedAt");
    if (eligible != reasons.isEmpty()) {
      throw new IllegalArgumentException("eligible decisions have no reasons, others at least one");
    }
  }
}
