package com.vasmarketing.campaign.domain.model;

import com.vasmarketing.platform.types.DomainRuleViolation;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The authorable part of a content variant, validated against its channel's constraints.
 *
 * @param subject required for channels that have one (EMAIL), must be null otherwise
 */
public record VariantContent(
    Channel channel, String variantKey, String language, String subject, String bodyTemplate) {

  private static final Pattern VARIANT_KEY = Pattern.compile("[A-Za-z0-9_-]{1,32}");
  private static final Pattern LANGUAGE = Pattern.compile("[a-z]{2}(-[A-Z]{2})?");
  private static final int MAX_SUBJECT_LENGTH = 150;

  public VariantContent {
    Objects.requireNonNull(channel, "channel");
    requireMatches(variantKey, VARIANT_KEY, "variantKey");
    requireMatches(language, LANGUAGE, "language");
    if (bodyTemplate == null || bodyTemplate.isBlank()) {
      throw invalid("body must not be blank");
    }
    if (bodyTemplate.length() > channel.maxBodyLength()) {
      throw invalid(
          "body exceeds " + channel.maxBodyLength() + " characters for " + channel.name());
    }
    if (channel.subjectRequired()) {
      if (subject == null || subject.isBlank() || subject.length() > MAX_SUBJECT_LENGTH) {
        throw invalid("subject of 1-" + MAX_SUBJECT_LENGTH + " characters is required");
      }
      TemplateSlots.validate(subject);
    } else if (subject != null) {
      throw invalid(channel.name().toLowerCase(Locale.ROOT) + " variants have no subject");
    }
    TemplateSlots.validate(bodyTemplate);
  }

  private static void requireMatches(String value, Pattern pattern, String field) {
    if (value == null || !pattern.matcher(value).matches()) {
      throw invalid(field + " is invalid");
    }
  }

  private static DomainRuleViolation invalid(String message) {
    return new DomainRuleViolation(CampaignErrors.INVALID_VARIANT, message);
  }
}
