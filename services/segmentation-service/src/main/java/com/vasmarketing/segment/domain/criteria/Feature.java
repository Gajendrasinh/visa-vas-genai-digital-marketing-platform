package com.vasmarketing.segment.domain.criteria;

import java.util.Arrays;
import java.util.Optional;

/**
 * The allow-list of customer features a segment may reference. Segment definitions can only use
 * these names; anything else is rejected at validation time.
 */
public enum Feature {
  TXN_COUNT_30D("txn_count_30d", Type.NUMBER),
  TXN_AMOUNT_AVG_30D("txn_amount_avg_30d", Type.NUMBER),
  TRAVEL_TXN_COUNT_90D("travel_txn_count_90d", Type.NUMBER),
  ENGAGEMENT_SCORE("engagement_score", Type.NUMBER),
  CAMPAIGN_INTERACTIONS_30D("campaign_interactions_30d", Type.NUMBER),
  OFFER_CONVERSIONS_90D("offer_conversions_90d", Type.NUMBER),
  DAYS_SINCE_LAST_ACTIVITY("days_since_last_activity", Type.NUMBER),
  MARKETING_CONSENT("marketing_consent", Type.BOOLEAN);

  /** Value type of a feature. */
  public enum Type {
    NUMBER,
    BOOLEAN
  }

  private final String key;
  private final Type type;

  Feature(String key, Type type) {
    this.key = key;
    this.type = type;
  }

  public String key() {
    return key;
  }

  public Type type() {
    return type;
  }

  public static Optional<Feature> fromKey(String key) {
    return Arrays.stream(values()).filter(f -> f.key.equals(key)).findFirst();
  }
}
