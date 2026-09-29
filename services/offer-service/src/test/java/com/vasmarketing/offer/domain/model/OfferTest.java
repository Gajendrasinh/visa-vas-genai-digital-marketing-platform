package com.vasmarketing.offer.domain.model;

import static com.vasmarketing.offer.OfferFixtures.ADMIN;
import static com.vasmarketing.offer.OfferFixtures.NOW;
import static com.vasmarketing.offer.OfferFixtures.travelRules;
import static com.vasmarketing.offer.OfferFixtures.travelTerms;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vasmarketing.offer.domain.eligibility.EligibilityRule;
import com.vasmarketing.platform.types.Money;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OfferTest {

  private static Offer draft() {
    return Offer.create(ADMIN, travelTerms("Travel 10%"), travelRules(), NOW);
  }

  @Test
  void activationRequiresConsentRule() {
    Offer offer =
        Offer.create(
            ADMIN,
            travelTerms("No consent"),
            List.of(new EligibilityRule.MinTransactions30d(1)),
            NOW);

    assertThatThrownBy(() -> offer.activate(NOW))
        .extracting("code")
        .isEqualTo(OfferErrors.CONSENT_RULE_REQUIRED);
  }

  @Test
  void activationRejectsEndedOffers() {
    Offer offer = draft();
    assertThatThrownBy(() -> offer.activate(NOW.plusSeconds(61 * 86_400L)))
        .extracting("code")
        .isEqualTo(OfferErrors.VALIDITY_ELAPSED);
  }

  @Test
  void rulesAndTermsEditableOnlyWhenNotLive() {
    Offer offer = draft();
    offer.activate(NOW);

    assertThatThrownBy(() -> offer.replaceRules(travelRules(), NOW))
        .extracting("code")
        .isEqualTo(OfferErrors.NOT_EDITABLE);
    assertThatThrownBy(() -> offer.updateTerms(travelTerms("Renamed"), NOW))
        .extracting("code")
        .isEqualTo(OfferErrors.NOT_EDITABLE);

    offer.pause(NOW);
    offer.replaceRules(travelRules(), NOW);
    offer.updateTerms(travelTerms("Renamed"), NOW);
    assertThat(offer.ruleSetVersion()).isEqualTo(2);
    assertThat(offer.terms().title()).isEqualTo("Renamed");
  }

  @Test
  void lifecycleTransitions() {
    Offer offer = draft();
    offer.activate(NOW);
    offer.pause(NOW);
    offer.activate(NOW);
    offer.markExhausted(NOW);
    assertThat(offer.status()).isEqualTo(OfferStatus.EXHAUSTED);
    assertThatThrownBy(() -> offer.pause(NOW))
        .extracting("code")
        .isEqualTo(OfferErrors.INVALID_TRANSITION);
    assertThatThrownBy(() -> offer.expire(NOW.plusSeconds(90 * 86_400L)))
        .extracting("code")
        .isEqualTo(OfferErrors.INVALID_TRANSITION);
  }

  @Test
  void expiryOnlyAfterValidityEnds() {
    Offer offer = draft();
    offer.activate(NOW);

    assertThatThrownBy(() -> offer.expire(NOW))
        .extracting("code")
        .isEqualTo(OfferErrors.INVALID_TRANSITION);
    offer.expire(NOW.plusSeconds(61 * 86_400L));
    assertThat(offer.status()).isEqualTo(OfferStatus.EXPIRED);
    assertThatThrownBy(() -> offer.activate(NOW))
        .extracting("code")
        .isEqualTo(OfferErrors.INVALID_TRANSITION);
  }

  @Test
  void rewardCalculationIsCappedAndDeterministic() {
    Reward percent = Reward.percent(RewardType.CASHBACK_PERCENT, "10", Money.of("50", "USD"));
    assertThat(percent.rewardFor(Money.of("120.00", "USD"))).isEqualTo(Money.of("12", "USD"));
    assertThat(percent.rewardFor(Money.of("900", "USD"))).isEqualTo(Money.of("50", "USD"));

    Reward fixed = Reward.fixed(Money.of("15", "USD"), Money.of("15", "USD"));
    assertThat(fixed.rewardFor(Money.of("1000", "USD"))).isEqualTo(Money.of("15", "USD"));
  }

  @Test
  void rewardShapeIsValidated() {
    Money cap = Money.of("50", "USD");
    assertThatThrownBy(() -> Reward.percent(RewardType.CASHBACK_PERCENT, "51", cap))
        .extracting("code")
        .isEqualTo(OfferErrors.INVALID_REWARD);
    assertThatThrownBy(() -> Reward.percent(RewardType.CASHBACK_PERCENT, "10.125", cap))
        .extracting("code")
        .isEqualTo(OfferErrors.INVALID_REWARD);
    assertThat(Reward.percent(RewardType.CASHBACK_PERCENT, "10", cap))
        .isEqualTo(Reward.percent(RewardType.CASHBACK_PERCENT, "10.00", cap));
    assertThatThrownBy(() -> Reward.percent(RewardType.DISCOUNT_PERCENT, "0", cap))
        .extracting("code")
        .isEqualTo(OfferErrors.INVALID_REWARD);
    assertThatThrownBy(() -> Reward.fixed(Money.of("60", "USD"), cap))
        .extracting("code")
        .isEqualTo(OfferErrors.INVALID_REWARD);
    assertThatThrownBy(() -> new Reward(RewardType.CASHBACK_FIXED, BigDecimal.ONE, cap, cap))
        .extracting("code")
        .isEqualTo(OfferErrors.INVALID_REWARD);
    assertThatThrownBy(() -> new Reward(RewardType.CASHBACK_PERCENT, BigDecimal.ONE, cap, cap))
        .extracting("code")
        .isEqualTo(OfferErrors.INVALID_REWARD);
  }

  @Test
  void termsAreValidated() {
    OfferTerms valid = travelTerms("Valid");
    assertThatThrownBy(
            () ->
                new OfferTerms(
                    UUID.randomUUID(),
                    valid.title(),
                    valid.description(),
                    valid.category(),
                    valid.reward(),
                    Money.of("10", "EUR"),
                    1,
                    valid.totalBudget(),
                    valid.validity(),
                    null))
        .hasMessageContaining("one currency");
    assertThatThrownBy(
            () ->
                new OfferTerms(
                    UUID.randomUUID(),
                    valid.title(),
                    valid.description(),
                    valid.category(),
                    valid.reward(),
                    valid.minSpend(),
                    0,
                    valid.totalBudget(),
                    valid.validity(),
                    null))
        .hasMessageContaining("perCustomerCap");
    assertThatThrownBy(() -> new Validity(NOW, NOW))
        .extracting("code")
        .isEqualTo(OfferErrors.INVALID_OFFER);
  }
}
