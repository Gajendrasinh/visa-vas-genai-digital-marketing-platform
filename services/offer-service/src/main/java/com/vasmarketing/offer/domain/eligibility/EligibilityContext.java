package com.vasmarketing.offer.domain.eligibility;

import com.vasmarketing.offer.domain.model.OfferCategory;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Facts about one customer that eligibility rules may use. Assembled from events (segment
 * membership, profile features, consent) and the redemption ledger, never from model output.
 */
public record EligibilityContext(
    UUID customerId,
    Set<UUID> segmentIds,
    String countryCode,
    int transactionCount30d,
    Map<OfferCategory, Integer> categoryTransactions90d,
    boolean marketingConsent,
    int priorRedemptions) {

  public EligibilityContext {
    Objects.requireNonNull(customerId, "customerId");
    segmentIds = Set.copyOf(segmentIds);
    Objects.requireNonNull(countryCode, "countryCode");
    categoryTransactions90d = Map.copyOf(categoryTransactions90d);
    if (transactionCount30d < 0 || priorRedemptions < 0) {
      throw new IllegalArgumentException("counts must not be negative");
    }
  }

  public int categoryTransactions90d(OfferCategory category) {
    return categoryTransactions90d.getOrDefault(category, 0);
  }
}
