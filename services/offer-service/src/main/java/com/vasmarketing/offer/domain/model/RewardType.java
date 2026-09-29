package com.vasmarketing.offer.domain.model;

public enum RewardType {
  /** Percentage of the qualifying spend returned as cashback, capped by {@code maxReward}. */
  CASHBACK_PERCENT,
  /** Fixed cashback amount per qualifying purchase. */
  CASHBACK_FIXED,
  /** Percentage discount applied by the merchant, capped by {@code maxReward}. */
  DISCOUNT_PERCENT
}
