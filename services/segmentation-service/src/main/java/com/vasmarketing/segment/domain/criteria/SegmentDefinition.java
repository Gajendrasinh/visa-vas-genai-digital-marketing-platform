package com.vasmarketing.segment.domain.criteria;

import com.vasmarketing.platform.types.DomainRuleViolation;
import java.util.Objects;

/** A validated segment definition with bounded size, so evaluation cost stays predictable. */
public record SegmentDefinition(Criterion root) {

  public static final int MAX_CONDITIONS = 20;
  public static final int MAX_DEPTH = 4;

  public SegmentDefinition {
    Objects.requireNonNull(root, "root");
    if (root.conditionCount() > MAX_CONDITIONS) {
      throw invalid("at most " + MAX_CONDITIONS + " conditions are allowed");
    }
    if (root.depth() > MAX_DEPTH) {
      throw invalid("nesting deeper than " + MAX_DEPTH + " levels is not allowed");
    }
  }

  public boolean matches(FeatureVector features) {
    return root.matches(features);
  }

  private static DomainRuleViolation invalid(String message) {
    return new DomainRuleViolation(SegmentErrorCodes.INVALID_DEFINITION, message);
  }
}
