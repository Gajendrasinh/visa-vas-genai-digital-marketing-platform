package com.vasmarketing.platform.security;

/**
 * Fine-grained permissions checked by services ({@code @PreAuthorize("hasAuthority(...)")}). Roles
 * are coarse; code only ever checks permissions.
 */
public final class Permissions {

  public static final String CAMPAIGN_READ = "campaign:read";
  public static final String CAMPAIGN_DRAFT = "campaign:draft";
  public static final String CAMPAIGN_SUBMIT = "campaign:submit";
  public static final String CAMPAIGN_APPROVE = "campaign:approve";
  public static final String CAMPAIGN_PUBLISH = "campaign:publish";
  public static final String OFFER_READ = "offer:read";
  public static final String OFFER_WRITE = "offer:write";
  public static final String SEGMENT_READ = "segment:read";
  public static final String SEGMENT_WRITE = "segment:write";
  public static final String CUSTOMER_PROFILE_READ = "customer:profile:read";
  public static final String CUSTOMER_WRITE = "customer:write";
  public static final String MERCHANT_WRITE = "merchant:write";
  public static final String ANALYTICS_READ = "analytics:read";
  public static final String KNOWLEDGE_READ = "knowledge:read";
  public static final String KNOWLEDGE_WRITE = "knowledge:write";
  public static final String AI_CHAT = "ai:chat";
  public static final String AI_GENERATE = "ai:generate";
  public static final String AI_ADMIN = "ai:admin";
  public static final String AI_TELEMETRY_READ = "ai:telemetry:read";
  public static final String AUDIT_READ = "audit:read";

  private Permissions() {}
}
