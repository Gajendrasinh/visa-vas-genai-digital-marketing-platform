package com.vasmarketing.campaign.domain.model;

import com.vasmarketing.campaign.domain.event.CampaignEvent;
import com.vasmarketing.platform.types.Actor;
import com.vasmarketing.platform.types.DomainRuleViolation;
import com.vasmarketing.platform.types.TenantId;
import com.vasmarketing.platform.types.UserId;
import com.vasmarketing.platform.types.UuidV7;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Campaign aggregate. Enforces the lifecycle state machine, four-eyes approval and the rule that
 * only compliance-checked, approved content can reach customers.
 */
public final class Campaign {

  private final CampaignId id;
  private final TenantId tenantId;
  private final CampaignOrigin origin;
  private final UUID aiRequestId;
  private final UserId createdBy;
  private final Instant createdAt;
  private final Long version;
  private final List<ContentVariant> variants;
  private final List<Approval> approvals;
  private final List<CampaignEvent> pendingEvents = new ArrayList<>();
  private CampaignDetails details;
  private CampaignStatus status;
  private UserId publishedBy;
  private Instant publishedAt;
  private Instant updatedAt;

  private Campaign(State state) {
    this.id = Objects.requireNonNull(state.id(), "id");
    this.tenantId = Objects.requireNonNull(state.tenantId(), "tenantId");
    this.details = Objects.requireNonNull(state.details(), "details");
    this.status = Objects.requireNonNull(state.status(), "status");
    this.origin = Objects.requireNonNull(state.origin(), "origin");
    this.aiRequestId = state.aiRequestId();
    this.createdBy = Objects.requireNonNull(state.createdBy(), "createdBy");
    this.createdAt = Objects.requireNonNull(state.createdAt(), "createdAt");
    this.updatedAt = Objects.requireNonNull(state.updatedAt(), "updatedAt");
    this.publishedBy = state.publishedBy();
    this.publishedAt = state.publishedAt();
    this.variants = new ArrayList<>(state.variants());
    this.approvals = new ArrayList<>(state.approvals());
    this.version = state.version();
    if (origin == CampaignOrigin.AI_ASSISTED && aiRequestId == null) {
      throw new IllegalArgumentException("AI-assisted campaigns must reference their AI request");
    }
  }

  /** Persisted state, used to create and rehydrate the aggregate. */
  public record State(
      CampaignId id,
      TenantId tenantId,
      CampaignDetails details,
      CampaignStatus status,
      CampaignOrigin origin,
      UUID aiRequestId,
      UserId createdBy,
      Instant createdAt,
      Instant updatedAt,
      UserId publishedBy,
      Instant publishedAt,
      List<ContentVariant> variants,
      List<Approval> approvals,
      Long version) {

    public State {
      variants = List.copyOf(variants);
      approvals = List.copyOf(approvals);
    }
  }

  /** Creates a new campaign. Every campaign, including AI-drafted ones, starts as DRAFT. */
  public static Campaign create(
      Actor actor, CampaignDetails details, CampaignOrigin origin, UUID aiRequestId, Instant now) {
    Campaign campaign =
        new Campaign(
            new State(
                CampaignId.newId(),
                actor.tenantId(),
                details,
                CampaignStatus.DRAFT,
                origin,
                aiRequestId,
                actor.userId(),
                now,
                now,
                null,
                null,
                List.of(),
                List.of(),
                null));
    campaign.record(
        new CampaignEvent.CampaignCreated(campaign.id, campaign.tenantId, actor.userId(), now));
    return campaign;
  }

  public static Campaign restore(State state) {
    return new Campaign(state);
  }

  public void updateDetails(CampaignDetails newDetails, Instant now) {
    requireDraft();
    for (ContentVariant variant : variants) {
      if (!newDetails.channels().contains(variant.content().channel())) {
        throw new DomainRuleViolation(
            CampaignErrors.INVALID_DETAILS,
            "remove the " + variant.content().channel() + " variants before removing the channel");
      }
    }
    details = newDetails;
    updatedAt = now;
  }

  public VariantId addVariant(
      VariantContent content, ContentAuthor author, PromptReference prompt, Instant now) {
    requireDraft();
    if (!details.channels().contains(content.channel())) {
      throw new DomainRuleViolation(
          CampaignErrors.INVALID_VARIANT, "campaign does not use channel " + content.channel());
    }
    if (variants.stream().anyMatch(v -> v.sameSlotAs(content))) {
      throw new DomainRuleViolation(
          CampaignErrors.DUPLICATE_VARIANT,
          "variant " + content.variantKey() + " already exists for this channel and language");
    }
    ContentVariant variant =
        new ContentVariant(VariantId.newId(), content, author, prompt, VariantStatus.DRAFT, null);
    variants.add(variant);
    updatedAt = now;
    return variant.id();
  }

  public void removeVariant(VariantId variantId, Instant now) {
    requireDraft();
    variants.remove(variant(variantId));
    updatedAt = now;
  }

  /** Records the outcome of the compliance review of one variant. */
  public void recordCompliance(VariantId variantId, boolean passed, String report, Instant now) {
    requireDraft();
    variant(variantId).recordCompliance(passed, report);
    updatedAt = now;
  }

  public void submit(Actor actor, Instant now) {
    for (Channel channel : details.channels()) {
      boolean covered =
          variants.stream()
              .anyMatch(
                  v ->
                      v.content().channel() == channel
                          && v.status() == VariantStatus.COMPLIANCE_PASSED);
      if (!covered) {
        throw new DomainRuleViolation(
            CampaignErrors.MISSING_APPROVABLE_VARIANT,
            "channel " + channel + " has no variant that passed compliance review");
      }
    }
    transitionTo(CampaignStatus.PENDING_APPROVAL, now);
    record(new CampaignEvent.CampaignSubmitted(id, tenantId, actor.userId(), now));
  }

  /** Four-eyes rule: the creator of a campaign can never approve it. */
  public void approve(Actor approver, String comment, Instant now) {
    if (approver.userId().equals(createdBy)) {
      throw new DomainRuleViolation(
          CampaignErrors.SELF_APPROVAL, "a campaign cannot be approved by its creator");
    }
    transitionTo(CampaignStatus.APPROVED, now);
    variants.forEach(ContentVariant::approve);
    approvals.add(
        new Approval(UuidV7.generate(), ApprovalDecision.APPROVE, approver.userId(), comment, now));
    record(new CampaignEvent.CampaignApproved(id, tenantId, approver.userId(), now));
  }

  public void reject(Actor reviewer, String reason, Instant now) {
    if (reason == null || reason.isBlank()) {
      throw new DomainRuleViolation(CampaignErrors.INVALID_DETAILS, "a rejection needs a reason");
    }
    transitionTo(CampaignStatus.DRAFT, now);
    approvals.add(
        new Approval(UuidV7.generate(), ApprovalDecision.REJECT, reviewer.userId(), reason, now));
    record(new CampaignEvent.CampaignRejected(id, tenantId, reviewer.userId(), now, reason));
  }

  /** Publishes an approved campaign: ACTIVE if its start time has come, otherwise SCHEDULED. */
  public void publish(Actor actor, Instant now) {
    if (details.schedule().hasEndedAt(now)) {
      throw new DomainRuleViolation(
          CampaignErrors.SCHEDULE_ELAPSED, "the campaign end date has already passed");
    }
    boolean startNow = details.schedule().hasStartedAt(now);
    transitionTo(startNow ? CampaignStatus.ACTIVE : CampaignStatus.SCHEDULED, now);
    publishedBy = actor.userId();
    publishedAt = now;
    record(new CampaignEvent.CampaignPublished(id, tenantId, actor.userId(), now, startNow));
  }

  /** Called by the scheduler once a SCHEDULED campaign's start time is reached. */
  public void activate(UserId scheduler, Instant now) {
    if (!details.schedule().hasStartedAt(now)) {
      throw new DomainRuleViolation(
          CampaignErrors.INVALID_TRANSITION, "the campaign start time has not been reached");
    }
    transitionTo(CampaignStatus.ACTIVE, now);
    record(new CampaignEvent.CampaignActivated(id, tenantId, scheduler, now));
  }

  public void pause(Actor actor, Instant now) {
    transitionTo(CampaignStatus.PAUSED, now);
    record(new CampaignEvent.CampaignPaused(id, tenantId, actor.userId(), now));
  }

  public void resume(Actor actor, Instant now) {
    if (details.schedule().hasEndedAt(now)) {
      throw new DomainRuleViolation(
          CampaignErrors.SCHEDULE_ELAPSED, "an ended campaign cannot be resumed");
    }
    transitionTo(CampaignStatus.ACTIVE, now);
    record(new CampaignEvent.CampaignResumed(id, tenantId, actor.userId(), now));
  }

  public void complete(UserId actor, Instant now) {
    transitionTo(CampaignStatus.COMPLETED, now);
    record(new CampaignEvent.CampaignCompleted(id, tenantId, actor, now));
  }

  public void archive(Actor actor, Instant now) {
    transitionTo(CampaignStatus.ARCHIVED, now);
    record(new CampaignEvent.CampaignArchived(id, tenantId, actor.userId(), now));
  }

  /** Returns and clears the events recorded since the last call. */
  public List<CampaignEvent> pullEvents() {
    List<CampaignEvent> events = List.copyOf(pendingEvents);
    pendingEvents.clear();
    return events;
  }

  private void transitionTo(CampaignStatus target, Instant now) {
    if (!status.canTransitionTo(target)) {
      throw new DomainRuleViolation(
          CampaignErrors.INVALID_TRANSITION,
          "campaign " + id + " is " + status + "; cannot move to " + target);
    }
    status = target;
    updatedAt = now;
  }

  private void requireDraft() {
    if (status != CampaignStatus.DRAFT) {
      throw new DomainRuleViolation(
          CampaignErrors.NOT_EDITABLE,
          "campaign " + id + " is " + status + "; only DRAFT is editable");
    }
  }

  private ContentVariant variant(VariantId variantId) {
    return variants.stream()
        .filter(v -> v.id().equals(variantId))
        .findFirst()
        .orElseThrow(
            () ->
                new DomainRuleViolation(
                    CampaignErrors.VARIANT_NOT_FOUND,
                    "variant " + variantId.value() + " not found"));
  }

  private void record(CampaignEvent event) {
    pendingEvents.add(event);
  }

  public CampaignId id() {
    return id;
  }

  public TenantId tenantId() {
    return tenantId;
  }

  public CampaignDetails details() {
    return details;
  }

  public CampaignStatus status() {
    return status;
  }

  public CampaignOrigin origin() {
    return origin;
  }

  public Optional<UUID> aiRequestId() {
    return Optional.ofNullable(aiRequestId);
  }

  public UserId createdBy() {
    return createdBy;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant updatedAt() {
    return updatedAt;
  }

  public Optional<UserId> publishedBy() {
    return Optional.ofNullable(publishedBy);
  }

  public Optional<Instant> publishedAt() {
    return Optional.ofNullable(publishedAt);
  }

  public List<ContentVariant> variants() {
    return List.copyOf(variants);
  }

  public List<Approval> approvals() {
    return List.copyOf(approvals);
  }

  /** Optimistic-locking version; empty until first persisted. */
  public Optional<Long> version() {
    return Optional.ofNullable(version);
  }
}
