package com.vasmarketing.campaign.domain.model;

/**
 * Stable error codes of the campaign domain (mapped to HTTP problem types in the interfaces layer).
 */
public final class CampaignErrors {

  public static final String INVALID_TRANSITION = "CAMPAIGN_INVALID_TRANSITION";
  public static final String NOT_EDITABLE = "CAMPAIGN_NOT_EDITABLE";
  public static final String SELF_APPROVAL = "CAMPAIGN_SELF_APPROVAL";
  public static final String MISSING_APPROVABLE_VARIANT = "CAMPAIGN_MISSING_APPROVABLE_VARIANT";
  public static final String SCHEDULE_ELAPSED = "CAMPAIGN_SCHEDULE_ELAPSED";
  public static final String INVALID_SCHEDULE = "CAMPAIGN_INVALID_SCHEDULE";
  public static final String INVALID_DETAILS = "CAMPAIGN_INVALID_DETAILS";
  public static final String INVALID_VARIANT = "CAMPAIGN_INVALID_VARIANT";
  public static final String VARIANT_NOT_FOUND = "CAMPAIGN_VARIANT_NOT_FOUND";
  public static final String DUPLICATE_VARIANT = "CAMPAIGN_DUPLICATE_VARIANT";
  public static final String DUPLICATE_NAME = "CAMPAIGN_DUPLICATE_NAME";

  private CampaignErrors() {}
}
