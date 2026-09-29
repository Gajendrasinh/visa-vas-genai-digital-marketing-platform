package com.vasmarketing.segment.domain.criteria;

import java.util.Arrays;
import java.util.Optional;

public enum Operator {
  EQ("eq", true),
  NEQ("neq", true),
  GT("gt", false),
  GTE("gte", false),
  LT("lt", false),
  LTE("lte", false);

  private final String key;
  private final boolean appliesToBoolean;

  Operator(String key, boolean appliesToBoolean) {
    this.key = key;
    this.appliesToBoolean = appliesToBoolean;
  }

  public String key() {
    return key;
  }

  public boolean appliesTo(Feature.Type type) {
    return type == Feature.Type.NUMBER || appliesToBoolean;
  }

  /** Applies the operator to the result of {@code actual.compareTo(expected)}. */
  boolean test(int comparison) {
    return switch (this) {
      case EQ -> comparison == 0;
      case NEQ -> comparison != 0;
      case GT -> comparison > 0;
      case GTE -> comparison >= 0;
      case LT -> comparison < 0;
      case LTE -> comparison <= 0;
    };
  }

  public static Optional<Operator> fromKey(String key) {
    return Arrays.stream(values()).filter(o -> o.key.equals(key)).findFirst();
  }
}
