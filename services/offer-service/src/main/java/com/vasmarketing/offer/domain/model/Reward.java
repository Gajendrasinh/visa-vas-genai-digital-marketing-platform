package com.vasmarketing.offer.domain.model;

import com.vasmarketing.platform.types.DomainRuleViolation;
import com.vasmarketing.platform.types.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * What the customer gets. Percent rewards are capped by {@code maxReward}; fixed rewards pay {@code
 * fixedAmount}. Reward arithmetic is deterministic and never delegated to AI.
 *
 * @param percent 0 < percent <= 50 for percent types, null for fixed
 */
public record Reward(RewardType type, BigDecimal percent, Money fixedAmount, Money maxReward) {

  private static final BigDecimal MAX_PERCENT = new BigDecimal("50");
  private static final BigDecimal HUNDRED = new BigDecimal("100");
  private static final int PERCENT_SCALE = 2;

  public Reward {
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(maxReward, "maxReward");
    if (!maxReward.isPositive()) {
      throw invalid("maxReward must be positive");
    }
    if (type == RewardType.CASHBACK_FIXED) {
      if (fixedAmount == null || !fixedAmount.isPositive() || percent != null) {
        throw invalid("a fixed reward needs a positive fixedAmount and no percent");
      }
      if (fixedAmount.compareTo(maxReward) > 0) {
        throw invalid("fixedAmount cannot exceed maxReward");
      }
    } else {
      if (percent == null || percent.signum() <= 0 || percent.compareTo(MAX_PERCENT) > 0) {
        throw invalid("percent must be in (0, 50]");
      }
      if (fixedAmount != null) {
        throw invalid("a percent reward has no fixedAmount");
      }
      if (percent.stripTrailingZeros().scale() > PERCENT_SCALE) {
        throw invalid("percent allows at most " + PERCENT_SCALE + " decimal places");
      }
      percent = percent.setScale(PERCENT_SCALE, RoundingMode.UNNECESSARY);
    }
  }

  public static Reward percent(RewardType type, String percent, Money maxReward) {
    return new Reward(type, new BigDecimal(percent), null, maxReward);
  }

  public static Reward fixed(Money amount, Money maxReward) {
    return new Reward(RewardType.CASHBACK_FIXED, null, amount, maxReward);
  }

  /** Reward earned for a qualifying spend, never above {@code maxReward}. */
  public Money rewardFor(Money spend) {
    Money raw =
        type == RewardType.CASHBACK_FIXED
            ? fixedAmount
            : new Money(
                spend
                    .amount()
                    .multiply(percent)
                    .divide(HUNDRED, Money.SCALE, RoundingMode.HALF_EVEN),
                spend.currency());
    return raw.compareTo(maxReward) > 0 ? maxReward : raw;
  }

  private static DomainRuleViolation invalid(String message) {
    return new DomainRuleViolation(OfferErrors.INVALID_REWARD, message);
  }
}
