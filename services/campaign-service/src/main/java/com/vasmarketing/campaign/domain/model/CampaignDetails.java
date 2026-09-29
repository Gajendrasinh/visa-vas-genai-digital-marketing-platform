package com.vasmarketing.campaign.domain.model;

import com.vasmarketing.platform.types.DomainRuleViolation;
import com.vasmarketing.platform.types.Money;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** The editable definition of a campaign. References to segment and offer are IDs only. */
public record CampaignDetails(
    String name,
    String objective,
    UUID segmentId,
    UUID offerId,
    Schedule schedule,
    Money budget,
    Set<Channel> channels) {

  private static final int MAX_NAME = 120;
  private static final int MAX_OBJECTIVE = 500;

  public CampaignDetails {
    requireText(name, MAX_NAME, "name");
    requireText(objective, MAX_OBJECTIVE, "objective");
    Objects.requireNonNull(segmentId, "segmentId");
    Objects.requireNonNull(offerId, "offerId");
    Objects.requireNonNull(schedule, "schedule");
    Objects.requireNonNull(budget, "budget");
    if (budget.isNegative()) {
      throw invalid("budget must not be negative");
    }
    if (channels == null || channels.isEmpty()) {
      throw invalid("at least one channel is required");
    }
    channels = Set.copyOf(EnumSet.copyOf(channels));
  }

  private static void requireText(String value, int max, String field) {
    if (value == null || value.isBlank() || value.length() > max) {
      throw invalid(field + " must be 1-" + max + " characters");
    }
  }

  private static DomainRuleViolation invalid(String message) {
    return new DomainRuleViolation(CampaignErrors.INVALID_DETAILS, message);
  }
}
