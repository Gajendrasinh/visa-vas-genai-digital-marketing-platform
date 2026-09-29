package com.vasmarketing.customer.domain.merchant;

import com.vasmarketing.customer.domain.customer.SpendCategory;
import com.vasmarketing.platform.types.DomainRuleViolation;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * ISO 18245 merchant category code with a deterministic mapping to spend categories, based on the
 * publicly documented MCC ranges.
 */
public record MerchantCategoryCode(String value) {

  private static final Pattern FOUR_DIGITS = Pattern.compile("[0-9]{4}");
  private static final Set<Integer> TRAVEL = Set.of(4111, 4112, 4411, 4511, 4722, 7011, 7512);
  private static final Set<Integer> DINING = Set.of(5812, 5813, 5814);
  private static final Set<Integer> GROCERY = Set.of(5411, 5422, 5441, 5451, 5462, 5499);
  private static final Set<Integer> FUEL = Set.of(5541, 5542, 5983);
  private static final Set<Integer> ENTERTAINMENT = Set.of(7832, 7922, 7991, 7996, 7999);
  private static final Set<Integer> ONLINE = Set.of(5815, 5816, 5817, 5818, 5964, 5969);

  public MerchantCategoryCode {
    if (value == null || !FOUR_DIGITS.matcher(value).matches()) {
      throw new DomainRuleViolation(MerchantErrors.INVALID_MERCHANT, "MCC must be four digits");
    }
  }

  public SpendCategory category() {
    int code = Integer.parseInt(value);
    if (between(code, 3000, 3999) || TRAVEL.contains(code)) {
      return SpendCategory.TRAVEL; // airlines, car rental, lodging and travel services
    }
    if (DINING.contains(code)) {
      return SpendCategory.DINING;
    }
    if (GROCERY.contains(code)) {
      return SpendCategory.GROCERY;
    }
    if (FUEL.contains(code)) {
      return SpendCategory.FUEL;
    }
    if (ENTERTAINMENT.contains(code)) {
      return SpendCategory.ENTERTAINMENT;
    }
    if (ONLINE.contains(code)) {
      return SpendCategory.ONLINE;
    }
    if (between(code, 5300, 5399) || between(code, 5600, 5799) || between(code, 5900, 5999)) {
      return SpendCategory.RETAIL;
    }
    return SpendCategory.OTHER;
  }

  private static boolean between(int code, int from, int to) {
    return code >= from && code <= to;
  }
}
