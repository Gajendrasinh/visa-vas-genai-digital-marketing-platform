package com.vasmarketing.customer.domain.customer;

import com.vasmarketing.platform.types.DomainRuleViolation;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Channel opt-ins and interests. Marketing consent is tracked separately on the customer. */
public record Preferences(
    boolean emailOptIn,
    boolean pushOptIn,
    boolean smsOptIn,
    Set<SpendCategory> preferredCategories,
    String language) {

  private static final Pattern LANGUAGE = Pattern.compile("[a-z]{2}(-[A-Z]{2})?");

  public Preferences {
    Objects.requireNonNull(preferredCategories, "preferredCategories");
    preferredCategories =
        preferredCategories.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(preferredCategories));
    if (language == null || !LANGUAGE.matcher(language).matches()) {
      throw new DomainRuleViolation(
          CustomerErrors.INVALID_PROFILE, "language must be like en or en-GB");
    }
  }

  /** Default for new customers: no channel is opted in until the customer says so. */
  public static Preferences defaults(String language) {
    return new Preferences(false, false, false, Set.of(), language);
  }
}
