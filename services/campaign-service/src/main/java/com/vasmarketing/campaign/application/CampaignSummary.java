package com.vasmarketing.campaign.application;

import com.vasmarketing.campaign.domain.model.CampaignOrigin;
import com.vasmarketing.campaign.domain.model.CampaignStatus;
import java.time.Instant;
import java.util.UUID;

/** List view of a campaign: no variants or approvals, so listing never loads child collections. */
public record CampaignSummary(
    UUID id,
    String name,
    CampaignStatus status,
    UUID segmentId,
    UUID offerId,
    Instant startAt,
    Instant endAt,
    CampaignOrigin origin,
    Instant createdAt,
    long version) {}
