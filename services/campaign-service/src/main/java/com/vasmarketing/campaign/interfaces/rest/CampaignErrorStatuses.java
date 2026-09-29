package com.vasmarketing.campaign.interfaces.rest;

import static com.vasmarketing.campaign.domain.model.CampaignErrors.DUPLICATE_NAME;
import static com.vasmarketing.campaign.domain.model.CampaignErrors.DUPLICATE_VARIANT;
import static com.vasmarketing.campaign.domain.model.CampaignErrors.INVALID_DETAILS;
import static com.vasmarketing.campaign.domain.model.CampaignErrors.INVALID_SCHEDULE;
import static com.vasmarketing.campaign.domain.model.CampaignErrors.INVALID_TRANSITION;
import static com.vasmarketing.campaign.domain.model.CampaignErrors.INVALID_VARIANT;
import static com.vasmarketing.campaign.domain.model.CampaignErrors.MISSING_APPROVABLE_VARIANT;
import static com.vasmarketing.campaign.domain.model.CampaignErrors.NOT_EDITABLE;
import static com.vasmarketing.campaign.domain.model.CampaignErrors.SCHEDULE_ELAPSED;
import static com.vasmarketing.campaign.domain.model.CampaignErrors.SELF_APPROVAL;
import static com.vasmarketing.campaign.domain.model.CampaignErrors.VARIANT_NOT_FOUND;

import com.vasmarketing.platform.web.error.ErrorStatusMapping;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** HTTP semantics of campaign error codes (docs/api.md §1). */
@Component
class CampaignErrorStatuses implements ErrorStatusMapping {

  @Override
  public Map<String, HttpStatus> statuses() {
    return Map.ofEntries(
        Map.entry(INVALID_DETAILS, HttpStatus.BAD_REQUEST),
        Map.entry(INVALID_SCHEDULE, HttpStatus.BAD_REQUEST),
        Map.entry(INVALID_VARIANT, HttpStatus.BAD_REQUEST),
        Map.entry(VARIANT_NOT_FOUND, HttpStatus.NOT_FOUND),
        Map.entry(INVALID_TRANSITION, HttpStatus.CONFLICT),
        Map.entry(NOT_EDITABLE, HttpStatus.CONFLICT),
        Map.entry(MISSING_APPROVABLE_VARIANT, HttpStatus.CONFLICT),
        Map.entry(SCHEDULE_ELAPSED, HttpStatus.CONFLICT),
        Map.entry(DUPLICATE_NAME, HttpStatus.CONFLICT),
        Map.entry(DUPLICATE_VARIANT, HttpStatus.CONFLICT),
        // Four-eyes: the caller is not allowed to approve this particular campaign.
        Map.entry(SELF_APPROVAL, HttpStatus.FORBIDDEN));
  }
}
