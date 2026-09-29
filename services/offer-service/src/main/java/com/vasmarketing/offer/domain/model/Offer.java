package com.vasmarketing.offer.domain.model;

import com.vasmarketing.offer.domain.eligibility.EligibilityRule;
import com.vasmarketing.platform.types.Actor;
import com.vasmarketing.platform.types.DomainRuleViolation;
import com.vasmarketing.platform.types.TenantId;
import com.vasmarketing.platform.types.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Offer aggregate: commercial terms, eligibility rules and availability status. */
public final class Offer {

  private static final int MAX_RULES = 20;

  private final OfferId id;
  private final TenantId tenantId;
  private final UserId createdBy;
  private final Instant createdAt;
  private final Long version;
  private OfferTerms terms;
  private List<EligibilityRule> rules;
  private int ruleSetVersion;
  private OfferStatus status;
  private Instant updatedAt;

  private Offer(State state) {
    this.id = Objects.requireNonNull(state.id(), "id");
    this.tenantId = Objects.requireNonNull(state.tenantId(), "tenantId");
    this.terms = Objects.requireNonNull(state.terms(), "terms");
    this.rules = validRules(state.rules());
    this.ruleSetVersion = state.ruleSetVersion();
    this.status = Objects.requireNonNull(state.status(), "status");
    this.createdBy = Objects.requireNonNull(state.createdBy(), "createdBy");
    this.createdAt = Objects.requireNonNull(state.createdAt(), "createdAt");
    this.updatedAt = Objects.requireNonNull(state.updatedAt(), "updatedAt");
    this.version = state.version();
  }

  public record State(
      OfferId id,
      TenantId tenantId,
      OfferTerms terms,
      List<EligibilityRule> rules,
      int ruleSetVersion,
      OfferStatus status,
      UserId createdBy,
      Instant createdAt,
      Instant updatedAt,
      Long version) {

    public State {
      rules = List.copyOf(rules);
    }
  }

  public static Offer create(
      Actor actor, OfferTerms terms, List<EligibilityRule> rules, Instant now) {
    return new Offer(
        new State(
            OfferId.newId(),
            actor.tenantId(),
            terms,
            rules,
            1,
            OfferStatus.DRAFT,
            actor.userId(),
            now,
            now,
            null));
  }

  public static Offer restore(State state) {
    return new Offer(state);
  }

  public void updateTerms(OfferTerms newTerms, Instant now) {
    requireEditable();
    terms = newTerms;
    updatedAt = now;
  }

  /** Replaces the rule set; decisions record the version they were made with. */
  public void replaceRules(List<EligibilityRule> newRules, Instant now) {
    requireEditable();
    rules = validRules(newRules);
    ruleSetVersion++;
    updatedAt = now;
  }

  public void activate(Instant now) {
    if (status != OfferStatus.DRAFT && status != OfferStatus.PAUSED) {
      throw transition(OfferStatus.ACTIVE);
    }
    if (rules.stream().noneMatch(EligibilityRule.ConsentRequired.class::isInstance)) {
      throw new DomainRuleViolation(
          OfferErrors.CONSENT_RULE_REQUIRED, "an active offer must require marketing consent");
    }
    if (terms.validity().hasEndedAt(now)) {
      throw new DomainRuleViolation(OfferErrors.VALIDITY_ELAPSED, "the offer has already ended");
    }
    status = OfferStatus.ACTIVE;
    updatedAt = now;
  }

  public void pause(Instant now) {
    if (status != OfferStatus.ACTIVE) {
      throw transition(OfferStatus.PAUSED);
    }
    status = OfferStatus.PAUSED;
    updatedAt = now;
  }

  /** Called by the expiry job once validity ends. */
  public void expire(Instant now) {
    if (status == OfferStatus.EXPIRED || status == OfferStatus.EXHAUSTED) {
      throw transition(OfferStatus.EXPIRED);
    }
    if (!terms.validity().hasEndedAt(now)) {
      throw new DomainRuleViolation(OfferErrors.INVALID_TRANSITION, "the offer has not ended yet");
    }
    status = OfferStatus.EXPIRED;
    updatedAt = now;
  }

  /** Called when the budget ledger shows the total budget is fully committed. */
  public void markExhausted(Instant now) {
    if (status != OfferStatus.ACTIVE && status != OfferStatus.PAUSED) {
      throw transition(OfferStatus.EXHAUSTED);
    }
    status = OfferStatus.EXHAUSTED;
    updatedAt = now;
  }

  private void requireEditable() {
    if (!status.isEditable()) {
      throw new DomainRuleViolation(
          OfferErrors.NOT_EDITABLE, "offer " + id + " is " + status + "; pause it to edit");
    }
  }

  private DomainRuleViolation transition(OfferStatus target) {
    return new DomainRuleViolation(
        OfferErrors.INVALID_TRANSITION,
        "offer " + id + " is " + status + "; cannot become " + target);
  }

  private static List<EligibilityRule> validRules(List<EligibilityRule> rules) {
    if (rules.size() > MAX_RULES) {
      throw new DomainRuleViolation(OfferErrors.INVALID_RULE, "at most " + MAX_RULES + " rules");
    }
    return List.copyOf(rules);
  }

  public OfferId id() {
    return id;
  }

  public TenantId tenantId() {
    return tenantId;
  }

  public OfferTerms terms() {
    return terms;
  }

  public List<EligibilityRule> rules() {
    return List.copyOf(rules);
  }

  public int ruleSetVersion() {
    return ruleSetVersion;
  }

  public OfferStatus status() {
    return status;
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

  public Optional<Long> version() {
    return Optional.ofNullable(version);
  }
}
