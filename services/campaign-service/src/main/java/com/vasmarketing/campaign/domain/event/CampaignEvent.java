package com.vasmarketing.campaign.domain.event;

import com.vasmarketing.campaign.domain.model.CampaignId;
import com.vasmarketing.platform.types.TenantId;
import com.vasmarketing.platform.types.UserId;
import java.time.Instant;

/**
 * Facts recorded by the campaign aggregate. They become {@code campaign.events} messages through
 * the transactional outbox (Phase 4).
 */
public sealed interface CampaignEvent {

  CampaignId campaignId();

  TenantId tenantId();

  UserId actor();

  Instant occurredAt();

  record CampaignCreated(CampaignId campaignId, TenantId tenantId, UserId actor, Instant occurredAt)
      implements CampaignEvent {}

  record CampaignSubmitted(
      CampaignId campaignId, TenantId tenantId, UserId actor, Instant occurredAt)
      implements CampaignEvent {}

  record CampaignApproved(
      CampaignId campaignId, TenantId tenantId, UserId actor, Instant occurredAt)
      implements CampaignEvent {}

  record CampaignRejected(
      CampaignId campaignId, TenantId tenantId, UserId actor, Instant occurredAt, String reason)
      implements CampaignEvent {}

  record CampaignPublished(
      CampaignId campaignId,
      TenantId tenantId,
      UserId actor,
      Instant occurredAt,
      boolean activeImmediately)
      implements CampaignEvent {}

  record CampaignActivated(
      CampaignId campaignId, TenantId tenantId, UserId actor, Instant occurredAt)
      implements CampaignEvent {}

  record CampaignPaused(CampaignId campaignId, TenantId tenantId, UserId actor, Instant occurredAt)
      implements CampaignEvent {}

  record CampaignResumed(CampaignId campaignId, TenantId tenantId, UserId actor, Instant occurredAt)
      implements CampaignEvent {}

  record CampaignCompleted(
      CampaignId campaignId, TenantId tenantId, UserId actor, Instant occurredAt)
      implements CampaignEvent {}

  record CampaignArchived(
      CampaignId campaignId, TenantId tenantId, UserId actor, Instant occurredAt)
      implements CampaignEvent {}
}
