package com.vasmarketing.offer.domain.eligibility;

import static com.vasmarketing.offer.OfferFixtures.ADMIN;
import static com.vasmarketing.offer.OfferFixtures.NOW;
import static com.vasmarketing.offer.OfferFixtures.qualifyingCustomer;
import static com.vasmarketing.offer.OfferFixtures.travelRules;
import static com.vasmarketing.offer.OfferFixtures.travelTerms;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vasmarketing.offer.domain.model.Offer;
import com.vasmarketing.offer.domain.model.OfferCategory;
import com.vasmarketing.offer.domain.model.OfferErrors;
import com.vasmarketing.platform.types.Money;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EligibilityEngineTest {

  private static final Money BUDGET_LEFT = Money.of("5000", "USD");

  private final EligibilityEngine engine = new EligibilityEngine();
  private Offer offer;

  @BeforeEach
  void activeTravelOffer() {
    offer = Offer.create(ADMIN, travelTerms("Travel 10%"), travelRules(), NOW);
    offer.activate(NOW);
  }

  @Test
  void qualifyingCustomerIsEligibleWithNoReasons() {
    EligibilityDecision decision = engine.evaluate(offer, qualifyingCustomer(), BUDGET_LEFT, NOW);

    assertThat(decision.eligible()).isTrue();
    assertThat(decision.reasons()).isEmpty();
    assertThat(decision.ruleSetVersion()).isEqualTo(1);
    assertThat(decision.offerId()).isEqualTo(offer.id());
  }

  @Test
  void reportsEveryFailedRuleNotJustTheFirst() {
    EligibilityContext context =
        new EligibilityContext(UUID.randomUUID(), Set.of(), "GB", 1, Map.of(), false, 0);

    EligibilityDecision decision = engine.evaluate(offer, context, BUDGET_LEFT, NOW);

    assertThat(decision.eligible()).isFalse();
    assertThat(decision.reasons())
        .containsExactly(
            ReasonCode.NOT_IN_TARGET_SEGMENT,
            ReasonCode.COUNTRY_NOT_ELIGIBLE,
            ReasonCode.INSUFFICIENT_ACTIVITY,
            ReasonCode.LOW_CATEGORY_AFFINITY,
            ReasonCode.NO_MARKETING_CONSENT);
  }

  @Test
  void offerLevelAvailabilityIsChecked() {
    EligibilityContext capped =
        new EligibilityContext(
            UUID.randomUUID(),
            Set.of(com.vasmarketing.offer.OfferFixtures.TRAVEL_SEGMENT),
            "US",
            5,
            Map.of(OfferCategory.TRAVEL, 4),
            true,
            2);
    offer.pause(NOW);

    EligibilityDecision decision =
        engine.evaluate(
            offer, capped, Money.zero(BUDGET_LEFT.currency()), NOW.plusSeconds(90 * 86_400L));

    assertThat(decision.reasons())
        .containsExactly(
            ReasonCode.OFFER_NOT_ACTIVE,
            ReasonCode.OUTSIDE_VALIDITY,
            ReasonCode.BUDGET_EXHAUSTED,
            ReasonCode.CUSTOMER_CAP_REACHED);
  }

  @Test
  void countryMatchIsCaseInsensitiveForTheCustomer() {
    EligibilityContext lowerCase =
        new EligibilityContext(
            UUID.randomUUID(),
            Set.of(com.vasmarketing.offer.OfferFixtures.TRAVEL_SEGMENT),
            "us",
            5,
            Map.of(OfferCategory.TRAVEL, 4),
            true,
            0);

    assertThat(engine.evaluate(offer, lowerCase, BUDGET_LEFT, NOW).eligible()).isTrue();
  }

  @Test
  void decisionIsDeterministic() {
    EligibilityDecision first = engine.evaluate(offer, qualifyingCustomer(), BUDGET_LEFT, NOW);
    EligibilityDecision second = engine.evaluate(offer, qualifyingCustomer(), BUDGET_LEFT, NOW);

    assertThat(first).isEqualTo(second);
  }

  @Test
  void rulesValidateTheirParameters() {
    assertThatThrownBy(() -> new EligibilityRule.SegmentMembership(Set.of()))
        .extracting("code")
        .isEqualTo(OfferErrors.INVALID_RULE);
    assertThatThrownBy(() -> new EligibilityRule.CountryIn(Set.of("USA")))
        .extracting("code")
        .isEqualTo(OfferErrors.INVALID_RULE);
    assertThatThrownBy(() -> new EligibilityRule.MinTransactions30d(0))
        .extracting("code")
        .isEqualTo(OfferErrors.INVALID_RULE);
    assertThatThrownBy(() -> new EligibilityRule.CategoryAffinity(OfferCategory.DINING, 0))
        .extracting("code")
        .isEqualTo(OfferErrors.INVALID_RULE);
  }

  @Test
  void decisionInvariantLinksEligibilityAndReasons() {
    assertThatThrownBy(
            () ->
                new EligibilityDecision(
                    offer.id(),
                    UUID.randomUUID(),
                    true,
                    java.util.List.of(ReasonCode.BUDGET_EXHAUSTED),
                    1,
                    NOW))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
