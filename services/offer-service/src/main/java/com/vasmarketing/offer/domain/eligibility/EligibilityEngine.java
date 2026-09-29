package com.vasmarketing.offer.domain.eligibility;

import com.vasmarketing.offer.domain.model.Offer;
import com.vasmarketing.offer.domain.model.OfferStatus;
import com.vasmarketing.platform.types.Money;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic eligibility: offer-level availability checks plus every configured rule. Same
 * inputs always yield the same decision (ADR-019).
 */
public final class EligibilityEngine {

  public EligibilityDecision evaluate(
      Offer offer, EligibilityContext context, Money remainingBudget, Instant now) {
    List<ReasonCode> reasons = new ArrayList<>();
    if (offer.status() != OfferStatus.ACTIVE) {
      reasons.add(ReasonCode.OFFER_NOT_ACTIVE);
    }
    if (!offer.terms().validity().contains(now)) {
      reasons.add(ReasonCode.OUTSIDE_VALIDITY);
    }
    if (!remainingBudget.isPositive()) {
      reasons.add(ReasonCode.BUDGET_EXHAUSTED);
    }
    if (context.priorRedemptions() >= offer.terms().perCustomerCap()) {
      reasons.add(ReasonCode.CUSTOMER_CAP_REACHED);
    }
    for (EligibilityRule rule : offer.rules()) {
      rule.evaluate(context).ifPresent(reasons::add);
    }
    return new EligibilityDecision(
        offer.id(), context.customerId(), reasons.isEmpty(), reasons, offer.ruleSetVersion(), now);
  }
}
