package com.vasmarketing.offer.domain.model;

import com.vasmarketing.platform.types.DomainRuleViolation;
import com.vasmarketing.platform.types.Money;
import java.util.Objects;
import java.util.UUID;

/**
 * The commercial terms of an offer.
 *
 * @param termsDocumentId the knowledge-base document holding the legal terms (RAG source)
 */
public record OfferTerms(
    UUID merchantId,
    String title,
    String description,
    OfferCategory category,
    Reward reward,
    Money minSpend,
    int perCustomerCap,
    Money totalBudget,
    Validity validity,
    UUID termsDocumentId) {

  private static final int MAX_TITLE = 120;
  private static final int MAX_DESCRIPTION = 1_000;
  private static final int MAX_PER_CUSTOMER_CAP = 100;

  public OfferTerms {
    Objects.requireNonNull(merchantId, "merchantId");
    requireText(title, MAX_TITLE, "title");
    requireText(description, MAX_DESCRIPTION, "description");
    Objects.requireNonNull(category, "category");
    Objects.requireNonNull(reward, "reward");
    Objects.requireNonNull(minSpend, "minSpend");
    Objects.requireNonNull(totalBudget, "totalBudget");
    Objects.requireNonNull(validity, "validity");
    if (minSpend.isNegative()) {
      throw invalid("minSpend must not be negative");
    }
    if (!totalBudget.isPositive()) {
      throw invalid("totalBudget must be positive");
    }
    if (!sameCurrency(reward, minSpend, totalBudget)) {
      throw invalid("reward, minSpend and totalBudget must use one currency");
    }
    if (perCustomerCap < 1 || perCustomerCap > MAX_PER_CUSTOMER_CAP) {
      throw invalid("perCustomerCap must be 1-" + MAX_PER_CUSTOMER_CAP);
    }
  }

  private static boolean sameCurrency(Reward reward, Money minSpend, Money totalBudget) {
    return reward.maxReward().currency().equals(minSpend.currency())
        && minSpend.currency().equals(totalBudget.currency());
  }

  private static void requireText(String value, int max, String field) {
    if (value == null || value.isBlank() || value.length() > max) {
      throw invalid(field + " must be 1-" + max + " characters");
    }
  }

  private static DomainRuleViolation invalid(String message) {
    return new DomainRuleViolation(OfferErrors.INVALID_OFFER, message);
  }
}
