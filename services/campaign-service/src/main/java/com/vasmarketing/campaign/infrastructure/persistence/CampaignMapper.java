package com.vasmarketing.campaign.infrastructure.persistence;

import com.vasmarketing.campaign.domain.model.Approval;
import com.vasmarketing.campaign.domain.model.Campaign;
import com.vasmarketing.campaign.domain.model.CampaignDetails;
import com.vasmarketing.campaign.domain.model.CampaignId;
import com.vasmarketing.campaign.domain.model.ContentVariant;
import com.vasmarketing.campaign.domain.model.PromptReference;
import com.vasmarketing.campaign.domain.model.Schedule;
import com.vasmarketing.campaign.domain.model.VariantContent;
import com.vasmarketing.campaign.domain.model.VariantId;
import com.vasmarketing.platform.types.Money;
import com.vasmarketing.platform.types.TenantId;
import com.vasmarketing.platform.types.UserId;
import java.util.Currency;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Translates between the campaign aggregate and its JPA representation. */
final class CampaignMapper {

  private CampaignMapper() {}

  static Campaign toDomain(CampaignEntity e) {
    CampaignDetails details =
        new CampaignDetails(
            e.name,
            e.objective,
            e.segmentId,
            e.offerId,
            new Schedule(e.startAt, e.endAt),
            new Money(e.budgetAmount, Currency.getInstance(e.currency)),
            Set.copyOf(e.channels));
    return Campaign.restore(
        new Campaign.State(
            new CampaignId(e.id),
            new TenantId(e.tenantId),
            details,
            e.status,
            e.origin,
            e.aiRequestId,
            new UserId(e.createdBy),
            e.createdAt,
            e.updatedAt,
            e.publishedBy == null ? null : new UserId(e.publishedBy),
            e.publishedAt,
            e.variants.stream().map(CampaignMapper::toDomain).toList(),
            e.approvals.stream().map(CampaignMapper::toDomain).toList(),
            e.version));
  }

  /** Copies aggregate state onto a new or managed entity. */
  static void apply(Campaign campaign, CampaignEntity e) {
    CampaignDetails details = campaign.details();
    e.id = campaign.id().value();
    e.tenantId = campaign.tenantId().value();
    e.name = details.name();
    e.objective = details.objective();
    e.status = campaign.status();
    e.segmentId = details.segmentId();
    e.offerId = details.offerId();
    e.startAt = details.schedule().startAt();
    e.endAt = details.schedule().endAt();
    e.budgetAmount = details.budget().amount();
    e.currency = details.budget().currency().getCurrencyCode();
    e.origin = campaign.origin();
    e.aiRequestId = campaign.aiRequestId().orElse(null);
    e.createdBy = campaign.createdBy().value();
    e.publishedBy = campaign.publishedBy().map(UserId::value).orElse(null);
    e.publishedAt = campaign.publishedAt().orElse(null);
    e.createdAt = campaign.createdAt();
    e.updatedAt = campaign.updatedAt();
    e.channels.retainAll(details.channels());
    e.channels.addAll(EnumSet.copyOf(details.channels()));
    applyVariants(campaign, e);
    applyApprovals(campaign, e);
  }

  private static void applyVariants(Campaign campaign, CampaignEntity e) {
    Map<UUID, ContentVariantEntity> existing =
        e.variants.stream().collect(Collectors.toMap(v -> v.id, Function.identity()));
    Set<UUID> current =
        campaign.variants().stream().map(v -> v.id().value()).collect(Collectors.toSet());
    e.variants.removeIf(v -> !current.contains(v.id));
    for (ContentVariant variant : campaign.variants()) {
      ContentVariantEntity entity = existing.get(variant.id().value());
      if (entity == null) {
        entity = new ContentVariantEntity();
        e.variants.add(entity);
      }
      apply(variant, entity);
    }
  }

  private static void applyApprovals(Campaign campaign, CampaignEntity e) {
    Set<UUID> stored = e.approvals.stream().map(a -> a.id).collect(Collectors.toSet());
    for (Approval approval : campaign.approvals()) {
      if (!stored.contains(approval.id())) {
        ApprovalEntity entity = new ApprovalEntity();
        entity.id = approval.id();
        entity.decision = approval.decision();
        entity.decidedBy = approval.decidedBy().value();
        entity.comment = approval.comment();
        entity.decidedAt = approval.decidedAt();
        e.approvals.add(entity);
      }
    }
  }

  private static void apply(ContentVariant variant, ContentVariantEntity v) {
    VariantContent content = variant.content();
    v.id = variant.id().value();
    v.channel = content.channel();
    v.variantKey = content.variantKey();
    v.language = content.language();
    v.subject = content.subject();
    v.bodyTemplate = content.bodyTemplate();
    v.status = variant.status();
    v.generatedBy = variant.author();
    v.promptId = variant.promptReference().map(PromptReference::promptId).orElse(null);
    v.promptVersion = variant.promptReference().map(PromptReference::promptVersion).orElse(null);
    v.modelId = variant.promptReference().map(PromptReference::modelId).orElse(null);
    v.complianceReport = variant.complianceReport().orElse(null);
  }

  private static ContentVariant toDomain(ContentVariantEntity v) {
    PromptReference prompt =
        v.promptId == null ? null : new PromptReference(v.promptId, v.promptVersion, v.modelId);
    return ContentVariant.restore(
        new VariantId(v.id),
        new VariantContent(v.channel, v.variantKey, v.language, v.subject, v.bodyTemplate),
        v.generatedBy,
        prompt,
        v.status,
        v.complianceReport);
  }

  private static Approval toDomain(ApprovalEntity a) {
    return new Approval(a.id, a.decision, new UserId(a.decidedBy), a.comment, a.decidedAt);
  }
}
