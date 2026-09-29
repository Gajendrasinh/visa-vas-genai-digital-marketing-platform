package com.vasmarketing.offer;

import com.vasmarketing.offer.domain.eligibility.EligibilityContext;
import com.vasmarketing.offer.domain.eligibility.EligibilityRule;
import com.vasmarketing.offer.domain.model.OfferCategory;
import com.vasmarketing.offer.domain.model.OfferTerms;
import com.vasmarketing.offer.domain.model.Reward;
import com.vasmarketing.offer.domain.model.RewardType;
import com.vasmarketing.offer.domain.model.Validity;
import com.vasmarketing.platform.types.Actor;
import com.vasmarketing.platform.types.Money;
import com.vasmarketing.platform.types.TenantId;
import com.vasmarketing.platform.types.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class OfferFixtures {

  public static final Instant NOW = Instant.parse("2026-10-01T09:00:00Z");
  public static final TenantId TENANT =
      new TenantId(UUID.fromString("0191f000-0000-7000-8000-000000000001"));
  public static final Actor ADMIN = new Actor(new UserId("admin"), TENANT);
  public static final UUID TRAVEL_SEGMENT = UUID.fromString("0191f000-0000-7000-8000-00000000a001");

  private OfferFixtures() {}

  public static OfferTerms travelTerms(String title) {
    return new OfferTerms(
        UUID.fromString("0191f000-0000-7000-8000-00000000c001"),
        title,
        "10% back on flights, up to $50",
        OfferCategory.TRAVEL,
        Reward.percent(RewardType.CASHBACK_PERCENT, "10", Money.of("50", "USD")),
        Money.of("100", "USD"),
        2,
        Money.of("100000", "USD"),
        new Validity(NOW.minusSeconds(86_400), NOW.plusSeconds(60 * 86_400L)),
        null);
  }

  public static List<EligibilityRule> travelRules() {
    return List.of(
        new EligibilityRule.SegmentMembership(Set.of(TRAVEL_SEGMENT)),
        new EligibilityRule.CountryIn(Set.of("US", "CA")),
        new EligibilityRule.MinTransactions30d(3),
        new EligibilityRule.CategoryAffinity(OfferCategory.TRAVEL, 2),
        new EligibilityRule.ConsentRequired());
  }

  public static EligibilityContext qualifyingCustomer() {
    return new EligibilityContext(
        UUID.fromString("0191f000-0000-7000-8000-00000000d001"),
        Set.of(TRAVEL_SEGMENT),
        "US",
        5,
        Map.of(OfferCategory.TRAVEL, 4),
        true,
        0);
  }
}
