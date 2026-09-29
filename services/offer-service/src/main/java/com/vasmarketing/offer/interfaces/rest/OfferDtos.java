package com.vasmarketing.offer.interfaces.rest;

import com.vasmarketing.offer.application.OfferSummary;
import com.vasmarketing.offer.domain.eligibility.EligibilityRule;
import com.vasmarketing.offer.domain.model.Offer;
import com.vasmarketing.offer.domain.model.OfferCategory;
import com.vasmarketing.offer.domain.model.OfferErrors;
import com.vasmarketing.offer.domain.model.OfferStatus;
import com.vasmarketing.offer.domain.model.OfferTerms;
import com.vasmarketing.offer.domain.model.Reward;
import com.vasmarketing.offer.domain.model.RewardType;
import com.vasmarketing.offer.domain.model.Validity;
import com.vasmarketing.platform.types.CursorPage;
import com.vasmarketing.platform.types.DomainRuleViolation;
import com.vasmarketing.platform.types.Money;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Request/response bodies of the offer API. */
final class OfferDtos {

  private static final String AMOUNT = "^\\d{1,15}(\\.\\d{1,4})?$";

  private OfferDtos() {}

  record RewardDto(
      @NotNull RewardType type,
      @Pattern(regexp = "^\\d{1,2}(\\.\\d{1,2})?$") String percent,
      @Pattern(regexp = AMOUNT) String fixedAmount,
      @NotNull @Pattern(regexp = AMOUNT) String maxReward) {

    Reward toReward(Currency currency) {
      return new Reward(
          type,
          percent == null ? null : new BigDecimal(percent),
          fixedAmount == null ? null : new Money(new BigDecimal(fixedAmount), currency),
          new Money(new BigDecimal(maxReward), currency));
    }

    static RewardDto of(Reward reward) {
      return new RewardDto(
          reward.type(),
          reward.percent() == null ? null : reward.percent().toPlainString(),
          reward.fixedAmount() == null ? null : reward.fixedAmount().amount().toPlainString(),
          reward.maxReward().amount().toPlainString());
    }
  }

  /**
   * Flat rule representation: {@code type} selects which fields apply. Deliberately not a
   * polymorphic JSON type, so the payload can never choose a class to instantiate.
   */
  record RuleDto(
      @NotNull RuleType type,
      Set<UUID> segmentIds,
      Set<@Pattern(regexp = "^[A-Z]{2}$") String> countryCodes,
      @Min(1) Integer minimum,
      OfferCategory category,
      @Min(1) Integer minTransactions90d) {

    EligibilityRule toRule() {
      return switch (type) {
        case SEGMENT_MEMBERSHIP ->
            new EligibilityRule.SegmentMembership(required(segmentIds, "segmentIds"));
        case COUNTRY_IN -> new EligibilityRule.CountryIn(required(countryCodes, "countryCodes"));
        case MIN_TRANSACTIONS_30D ->
            new EligibilityRule.MinTransactions30d(required(minimum, "minimum"));
        case CATEGORY_AFFINITY ->
            new EligibilityRule.CategoryAffinity(
                required(category, "category"), required(minTransactions90d, "minTransactions90d"));
        case CONSENT_REQUIRED -> new EligibilityRule.ConsentRequired();
      };
    }

    static RuleDto of(EligibilityRule rule) {
      return switch (rule) {
        case EligibilityRule.SegmentMembership r ->
            new RuleDto(RuleType.SEGMENT_MEMBERSHIP, r.segmentIds(), null, null, null, null);
        case EligibilityRule.CountryIn r ->
            new RuleDto(RuleType.COUNTRY_IN, null, r.countryCodes(), null, null, null);
        case EligibilityRule.MinTransactions30d r ->
            new RuleDto(RuleType.MIN_TRANSACTIONS_30D, null, null, r.minimum(), null, null);
        case EligibilityRule.CategoryAffinity r ->
            new RuleDto(
                RuleType.CATEGORY_AFFINITY, null, null, null, r.category(), r.minTransactions90d());
        case EligibilityRule.ConsentRequired r ->
            new RuleDto(RuleType.CONSENT_REQUIRED, null, null, null, null, null);
      };
    }

    private <T> T required(T value, String field) {
      if (value == null) {
        throw new DomainRuleViolation(OfferErrors.INVALID_RULE, type + " rule requires " + field);
      }
      return value;
    }
  }

  enum RuleType {
    SEGMENT_MEMBERSHIP,
    COUNTRY_IN,
    MIN_TRANSACTIONS_30D,
    CATEGORY_AFFINITY,
    CONSENT_REQUIRED
  }

  record OfferTermsRequest(
      @NotNull UUID merchantId,
      @NotBlank @Size(max = 120) String title,
      @NotBlank @Size(max = 1000) String description,
      @NotNull OfferCategory category,
      @NotNull @Pattern(regexp = "^[A-Z]{3}$") String currency,
      @NotNull @Valid RewardDto reward,
      @NotNull @Pattern(regexp = AMOUNT) String minSpend,
      @Min(1) @Max(100) int perCustomerCap,
      @NotNull @Pattern(regexp = AMOUNT) String totalBudget,
      @NotNull Instant startAt,
      @NotNull Instant endAt,
      UUID termsDocumentId) {

    OfferTerms toTerms() {
      Currency cur = Currency.getInstance(currency);
      return new OfferTerms(
          merchantId,
          title,
          description,
          category,
          reward.toReward(cur),
          new Money(new BigDecimal(minSpend), cur),
          perCustomerCap,
          new Money(new BigDecimal(totalBudget), cur),
          new Validity(startAt, endAt),
          termsDocumentId);
    }
  }

  record CreateOfferRequest(
      @NotNull @Valid OfferTermsRequest terms,
      @NotNull @Size(max = 20) List<@Valid @NotNull RuleDto> rules) {
    List<EligibilityRule> toRules() {
      return rules.stream().map(RuleDto::toRule).toList();
    }
  }

  record ReplaceRulesRequest(@NotNull @Size(max = 20) List<@Valid @NotNull RuleDto> rules) {
    List<EligibilityRule> toRules() {
      return rules.stream().map(RuleDto::toRule).toList();
    }
  }

  record OfferResponse(
      UUID id,
      UUID merchantId,
      String title,
      String description,
      OfferCategory category,
      String currency,
      RewardDto reward,
      String minSpend,
      int perCustomerCap,
      String totalBudget,
      Instant startAt,
      Instant endAt,
      UUID termsDocumentId,
      OfferStatus status,
      int ruleSetVersion,
      List<RuleDto> rules,
      String createdBy,
      Instant createdAt,
      Instant updatedAt,
      long version) {

    static OfferResponse of(Offer o) {
      OfferTerms t = o.terms();
      return new OfferResponse(
          o.id().value(),
          t.merchantId(),
          t.title(),
          t.description(),
          t.category(),
          t.totalBudget().currency().getCurrencyCode(),
          RewardDto.of(t.reward()),
          t.minSpend().amount().toPlainString(),
          t.perCustomerCap(),
          t.totalBudget().amount().toPlainString(),
          t.validity().startAt(),
          t.validity().endAt(),
          t.termsDocumentId(),
          o.status(),
          o.ruleSetVersion(),
          o.rules().stream().map(RuleDto::of).toList(),
          o.createdBy().value(),
          o.createdAt(),
          o.updatedAt(),
          o.version().orElseThrow());
    }
  }

  record OfferPage(List<OfferSummary> items, String nextCursor) {
    static OfferPage of(CursorPage<OfferSummary> page) {
      return new OfferPage(page.items(), page.nextCursor().orElse(null));
    }
  }
}
