package com.vasmarketing.offer.domain.eligibility;

import com.vasmarketing.offer.domain.model.OfferCategory;
import com.vasmarketing.offer.domain.model.OfferErrors;
import com.vasmarketing.platform.types.DomainRuleViolation;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * A closed set of rule types, each implemented in code. There is deliberately no generic expression
 * rule: eligibility logic is reviewed, tested Java, not configuration that could be authored by a
 * model.
 */
public sealed interface EligibilityRule {

  /** Empty if the customer satisfies the rule, otherwise the reason they do not. */
  Optional<ReasonCode> evaluate(EligibilityContext context);

  /** Customer must belong to at least one of the target segments. */
  record SegmentMembership(Set<UUID> segmentIds) implements EligibilityRule {
    public SegmentMembership {
      if (segmentIds == null || segmentIds.isEmpty()) {
        throw invalid("segment rule needs at least one segment");
      }
      segmentIds = Set.copyOf(segmentIds);
    }

    @Override
    public Optional<ReasonCode> evaluate(EligibilityContext context) {
      boolean member = context.segmentIds().stream().anyMatch(segmentIds::contains);
      return member ? Optional.empty() : Optional.of(ReasonCode.NOT_IN_TARGET_SEGMENT);
    }
  }

  record CountryIn(Set<String> countryCodes) implements EligibilityRule {
    private static final Pattern ISO_ALPHA2 = Pattern.compile("[A-Z]{2}");

    public CountryIn {
      if (countryCodes == null || countryCodes.isEmpty()) {
        throw invalid("country rule needs at least one country");
      }
      countryCodes = Set.copyOf(countryCodes);
      if (!countryCodes.stream().allMatch(c -> ISO_ALPHA2.matcher(c).matches())) {
        throw invalid("countries must be ISO 3166-1 alpha-2 codes");
      }
    }

    @Override
    public Optional<ReasonCode> evaluate(EligibilityContext context) {
      return countryCodes.contains(context.countryCode().toUpperCase(Locale.ROOT))
          ? Optional.empty()
          : Optional.of(ReasonCode.COUNTRY_NOT_ELIGIBLE);
    }
  }

  record MinTransactions30d(int minimum) implements EligibilityRule {
    public MinTransactions30d {
      if (minimum < 1) {
        throw invalid("minimum transactions must be at least 1");
      }
    }

    @Override
    public Optional<ReasonCode> evaluate(EligibilityContext context) {
      return context.transactionCount30d() >= minimum
          ? Optional.empty()
          : Optional.of(ReasonCode.INSUFFICIENT_ACTIVITY);
    }
  }

  record CategoryAffinity(OfferCategory category, int minTransactions90d)
      implements EligibilityRule {
    public CategoryAffinity {
      Objects.requireNonNull(category, "category");
      if (minTransactions90d < 1) {
        throw invalid("category affinity needs at least 1 transaction");
      }
    }

    @Override
    public Optional<ReasonCode> evaluate(EligibilityContext context) {
      return context.categoryTransactions90d(category) >= minTransactions90d
          ? Optional.empty()
          : Optional.of(ReasonCode.LOW_CATEGORY_AFFINITY);
    }
  }

  /** Mandatory on every active offer: marketing requires explicit consent. */
  record ConsentRequired() implements EligibilityRule {
    @Override
    public Optional<ReasonCode> evaluate(EligibilityContext context) {
      return context.marketingConsent()
          ? Optional.empty()
          : Optional.of(ReasonCode.NO_MARKETING_CONSENT);
    }
  }

  private static DomainRuleViolation invalid(String message) {
    return new DomainRuleViolation(OfferErrors.INVALID_RULE, message);
  }
}
