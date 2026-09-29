package com.vasmarketing.segment.domain.criteria;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/** One customer's feature values. A missing feature never satisfies a condition. */
public final class FeatureVector {

  private final Map<Feature, BigDecimal> numbers;
  private final Map<Feature, Boolean> booleans;

  private FeatureVector(Map<Feature, BigDecimal> numbers, Map<Feature, Boolean> booleans) {
    this.numbers = numbers;
    this.booleans = booleans;
  }

  public static Builder builder() {
    return new Builder();
  }

  Optional<BigDecimal> number(Feature feature) {
    return Optional.ofNullable(numbers.get(feature));
  }

  Optional<Boolean> bool(Feature feature) {
    return Optional.ofNullable(booleans.get(feature));
  }

  /** Builds a vector, rejecting values of the wrong type for a feature. */
  public static final class Builder {
    private final Map<Feature, BigDecimal> numbers = new EnumMap<>(Feature.class);
    private final Map<Feature, Boolean> booleans = new EnumMap<>(Feature.class);

    public Builder number(Feature feature, BigDecimal value) {
      requireType(feature, Feature.Type.NUMBER);
      numbers.put(feature, value);
      return this;
    }

    public Builder number(Feature feature, long value) {
      return number(feature, BigDecimal.valueOf(value));
    }

    public Builder bool(Feature feature, boolean value) {
      requireType(feature, Feature.Type.BOOLEAN);
      booleans.put(feature, value);
      return this;
    }

    public FeatureVector build() {
      return new FeatureVector(new EnumMap<>(numbers), new EnumMap<>(booleans));
    }

    private static void requireType(Feature feature, Feature.Type type) {
      if (feature.type() != type) {
        throw new IllegalArgumentException(feature.key() + " is not a " + type + " feature");
      }
    }
  }
}
