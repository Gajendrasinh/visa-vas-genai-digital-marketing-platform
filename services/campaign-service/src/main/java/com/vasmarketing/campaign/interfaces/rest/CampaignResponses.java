package com.vasmarketing.campaign.interfaces.rest;

import com.vasmarketing.campaign.application.CampaignSummary;
import com.vasmarketing.campaign.domain.model.Approval;
import com.vasmarketing.campaign.domain.model.ApprovalDecision;
import com.vasmarketing.campaign.domain.model.Campaign;
import com.vasmarketing.campaign.domain.model.CampaignOrigin;
import com.vasmarketing.campaign.domain.model.CampaignStatus;
import com.vasmarketing.campaign.domain.model.Channel;
import com.vasmarketing.campaign.domain.model.ContentAuthor;
import com.vasmarketing.campaign.domain.model.ContentVariant;
import com.vasmarketing.campaign.domain.model.VariantStatus;
import com.vasmarketing.platform.types.CursorPage;
import com.vasmarketing.platform.types.Money;
import com.vasmarketing.platform.types.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Response bodies. Domain objects are never serialized directly. */
final class CampaignResponses {

  private CampaignResponses() {}

  record MoneyDto(String amount, String currency) {
    static MoneyDto of(Money money) {
      return new MoneyDto(money.amount().toPlainString(), money.currency().getCurrencyCode());
    }
  }

  record PromptDto(String promptId, String promptVersion, String modelId) {}

  record VariantDto(
      UUID id,
      Channel channel,
      String variantKey,
      String language,
      String subject,
      String bodyTemplate,
      VariantStatus status,
      ContentAuthor generatedBy,
      PromptDto prompt,
      String complianceReport) {

    static VariantDto of(ContentVariant v) {
      return new VariantDto(
          v.id().value(),
          v.content().channel(),
          v.content().variantKey(),
          v.content().language(),
          v.content().subject(),
          v.content().bodyTemplate(),
          v.status(),
          v.author(),
          v.promptReference()
              .map(p -> new PromptDto(p.promptId(), p.promptVersion(), p.modelId()))
              .orElse(null),
          v.complianceReport().orElse(null));
    }
  }

  record ApprovalDto(
      UUID id, ApprovalDecision decision, String decidedBy, String comment, Instant decidedAt) {
    static ApprovalDto of(Approval a) {
      return new ApprovalDto(
          a.id(), a.decision(), a.decidedBy().value(), a.comment(), a.decidedAt());
    }
  }

  record CampaignResponse(
      UUID id,
      String name,
      String objective,
      CampaignStatus status,
      UUID segmentId,
      UUID offerId,
      Instant startAt,
      Instant endAt,
      MoneyDto budget,
      Set<Channel> channels,
      CampaignOrigin origin,
      UUID aiRequestId,
      String createdBy,
      Instant createdAt,
      Instant updatedAt,
      String publishedBy,
      Instant publishedAt,
      long version,
      List<VariantDto> variants,
      List<ApprovalDto> approvals) {

    static CampaignResponse of(Campaign c) {
      return new CampaignResponse(
          c.id().value(),
          c.details().name(),
          c.details().objective(),
          c.status(),
          c.details().segmentId(),
          c.details().offerId(),
          c.details().schedule().startAt(),
          c.details().schedule().endAt(),
          MoneyDto.of(c.details().budget()),
          c.details().channels().stream()
              .sorted()
              .collect(Collectors.toCollection(java.util.LinkedHashSet::new)),
          c.origin(),
          c.aiRequestId().orElse(null),
          c.createdBy().value(),
          c.createdAt(),
          c.updatedAt(),
          c.publishedBy().map(UserId::value).orElse(null),
          c.publishedAt().orElse(null),
          c.version().orElseThrow(),
          c.variants().stream().map(VariantDto::of).toList(),
          c.approvals().stream().map(ApprovalDto::of).toList());
    }
  }

  record CampaignPage(List<CampaignSummary> items, String nextCursor) {
    static CampaignPage of(CursorPage<CampaignSummary> page) {
      return new CampaignPage(page.items(), page.nextCursor().orElse(null));
    }
  }

  record VariantCreated(UUID variantId, CampaignResponse campaign) {}
}
