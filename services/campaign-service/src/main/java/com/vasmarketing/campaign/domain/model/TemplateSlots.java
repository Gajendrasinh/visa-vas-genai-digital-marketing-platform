package com.vasmarketing.campaign.domain.model;

import com.vasmarketing.platform.types.DomainRuleViolation;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Template slots such as {@code {{offer.value}}}. Only allow-listed slots may appear: they are
 * filled at send time from authoritative records, which is how generated content is kept from
 * stating unverified facts (architecture.md §8.1).
 */
public final class TemplateSlots {

  public static final Set<String> ALLOWED =
      Set.of(
          "customer.firstName",
          "offer.title",
          "offer.value",
          "offer.minSpend",
          "offer.endDate",
          "merchant.name");

  private static final Pattern SLOT = Pattern.compile("\\{\\{\\s*([^{}]*?)\\s*}}");
  private static final Pattern DANGLING_BRACES = Pattern.compile("\\{\\{|}}");

  private TemplateSlots() {}

  /** Returns the slots used in the template, rejecting unknown or malformed ones. */
  public static Set<String> validate(String template) {
    Set<String> slots = new LinkedHashSet<>();
    Matcher matcher = SLOT.matcher(template);
    while (matcher.find()) {
      String slot = matcher.group(1);
      if (!ALLOWED.contains(slot)) {
        throw new DomainRuleViolation(
            CampaignErrors.INVALID_VARIANT, "template slot not allowed: " + slot);
      }
      slots.add(slot);
    }
    if (DANGLING_BRACES.matcher(SLOT.matcher(template).replaceAll("")).find()) {
      throw new DomainRuleViolation(CampaignErrors.INVALID_VARIANT, "malformed template slot");
    }
    return slots;
  }
}
