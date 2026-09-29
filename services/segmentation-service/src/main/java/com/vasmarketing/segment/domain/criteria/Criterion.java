package com.vasmarketing.segment.domain.criteria;

import com.vasmarketing.platform.types.DomainRuleViolation;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Segment criteria as a typed tree. The tree is evaluated in memory against feature vectors; it is
 * never translated into SQL, so a definition cannot inject queries.
 */
public sealed interface Criterion {

  boolean matches(FeatureVector features);

  /** Number of leaf conditions in this subtree. */
  int conditionCount();

  /** Nesting depth of this subtree (a condition has depth 1). */
  int depth();

  record All(List<Criterion> criteria) implements Criterion {
    public All {
      requireNonEmpty(criteria);
      criteria = List.copyOf(criteria);
    }

    @Override
    public boolean matches(FeatureVector features) {
      return criteria.stream().allMatch(c -> c.matches(features));
    }

    @Override
    public int conditionCount() {
      return criteria.stream().mapToInt(Criterion::conditionCount).sum();
    }

    @Override
    public int depth() {
      return 1 + criteria.stream().mapToInt(Criterion::depth).max().orElse(0);
    }
  }

  record Any(List<Criterion> criteria) implements Criterion {
    public Any {
      requireNonEmpty(criteria);
      criteria = List.copyOf(criteria);
    }

    @Override
    public boolean matches(FeatureVector features) {
      return criteria.stream().anyMatch(c -> c.matches(features));
    }

    @Override
    public int conditionCount() {
      return criteria.stream().mapToInt(Criterion::conditionCount).sum();
    }

    @Override
    public int depth() {
      return 1 + criteria.stream().mapToInt(Criterion::depth).max().orElse(0);
    }
  }

  /** Numeric comparison, e.g. {@code travel_txn_count_90d >= 3}. */
  record NumberCondition(Feature feature, Operator operator, BigDecimal value)
      implements Criterion {
    public NumberCondition {
      Objects.requireNonNull(value, "value");
      requireCompatible(feature, operator, Feature.Type.NUMBER);
    }

    @Override
    public boolean matches(FeatureVector features) {
      return features.number(feature).map(v -> operator.test(v.compareTo(value))).orElse(false);
    }

    @Override
    public int conditionCount() {
      return 1;
    }

    @Override
    public int depth() {
      return 1;
    }
  }

  /** Boolean equality, e.g. {@code marketing_consent = true}. */
  record BooleanCondition(Feature feature, Operator operator, boolean value) implements Criterion {
    public BooleanCondition {
      requireCompatible(feature, operator, Feature.Type.BOOLEAN);
    }

    @Override
    public boolean matches(FeatureVector features) {
      return features
          .bool(feature)
          .map(v -> operator.test(Boolean.compare(v, value)))
          .orElse(false);
    }

    @Override
    public int conditionCount() {
      return 1;
    }

    @Override
    public int depth() {
      return 1;
    }
  }

  private static void requireNonEmpty(List<Criterion> criteria) {
    if (criteria == null || criteria.isEmpty()) {
      throw invalid("a group needs at least one criterion");
    }
  }

  private static void requireCompatible(Feature feature, Operator operator, Feature.Type type) {
    Objects.requireNonNull(feature, "feature");
    Objects.requireNonNull(operator, "operator");
    if (feature.type() != type) {
      throw invalid(
          feature.key() + " is not a " + type.name().toLowerCase(Locale.ROOT) + " feature");
    }
    if (!operator.appliesTo(type)) {
      throw invalid("operator " + operator.key() + " cannot be used with " + feature.key());
    }
  }

  private static DomainRuleViolation invalid(String message) {
    return new DomainRuleViolation(SegmentErrorCodes.INVALID_DEFINITION, message);
  }
}
