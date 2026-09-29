package com.vasmarketing.segment.domain.model;

import com.vasmarketing.platform.types.Actor;
import com.vasmarketing.platform.types.DomainRuleViolation;
import com.vasmarketing.platform.types.TenantId;
import com.vasmarketing.platform.types.UserId;
import com.vasmarketing.segment.domain.criteria.FeatureVector;
import com.vasmarketing.segment.domain.criteria.SegmentDefinition;
import com.vasmarketing.segment.domain.criteria.SegmentErrorCodes;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Segment aggregate. A definition can change only while DRAFT; an ACTIVE segment's definition is
 * frozen so that membership and the campaigns targeting it stay consistent.
 */
public final class Segment {

  private static final int MAX_NAME = 100;
  private static final int MAX_DESCRIPTION = 500;

  private final SegmentId id;
  private final TenantId tenantId;
  private final SegmentOrigin origin;
  private final UserId createdBy;
  private final Instant createdAt;
  private final Long version;
  private String name;
  private String description;
  private SegmentDefinition definition;
  private int definitionVersion;
  private SegmentStatus status;
  private Instant updatedAt;

  private Segment(State state) {
    this.id = Objects.requireNonNull(state.id(), "id");
    this.tenantId = Objects.requireNonNull(state.tenantId(), "tenantId");
    this.name = validName(state.name());
    this.description = validDescription(state.description());
    this.definition = Objects.requireNonNull(state.definition(), "definition");
    this.definitionVersion = state.definitionVersion();
    this.status = Objects.requireNonNull(state.status(), "status");
    this.origin = Objects.requireNonNull(state.origin(), "origin");
    this.createdBy = Objects.requireNonNull(state.createdBy(), "createdBy");
    this.createdAt = Objects.requireNonNull(state.createdAt(), "createdAt");
    this.updatedAt = Objects.requireNonNull(state.updatedAt(), "updatedAt");
    this.version = state.version();
  }

  public record State(
      SegmentId id,
      TenantId tenantId,
      String name,
      String description,
      SegmentDefinition definition,
      int definitionVersion,
      SegmentStatus status,
      SegmentOrigin origin,
      UserId createdBy,
      Instant createdAt,
      Instant updatedAt,
      Long version) {}

  public static Segment create(
      Actor actor,
      String name,
      String description,
      SegmentDefinition definition,
      SegmentOrigin origin,
      Instant now) {
    return new Segment(
        new State(
            SegmentId.newId(),
            actor.tenantId(),
            name,
            description,
            definition,
            1,
            SegmentStatus.DRAFT,
            origin,
            actor.userId(),
            now,
            now,
            null));
  }

  public static Segment restore(State state) {
    return new Segment(state);
  }

  public void describe(String newName, String newDescription, Instant now) {
    name = validName(newName);
    description = validDescription(newDescription);
    updatedAt = now;
  }

  public void redefine(SegmentDefinition newDefinition, Instant now) {
    if (status != SegmentStatus.DRAFT) {
      throw new DomainRuleViolation(
          SegmentErrorCodes.NOT_EDITABLE,
          "segment " + id + " is " + status + "; create a new segment to change its definition");
    }
    definition = Objects.requireNonNull(newDefinition, "definition");
    definitionVersion++;
    updatedAt = now;
  }

  public void activate(Instant now) {
    transition(SegmentStatus.DRAFT, SegmentStatus.ACTIVE, now);
  }

  public void retire(Instant now) {
    transition(SegmentStatus.ACTIVE, SegmentStatus.RETIRED, now);
  }

  /** Deterministic membership test; the only way customers enter a segment. */
  public boolean includes(FeatureVector features) {
    return status == SegmentStatus.ACTIVE && definition.matches(features);
  }

  private void transition(SegmentStatus from, SegmentStatus to, Instant now) {
    if (status != from) {
      throw new DomainRuleViolation(
          SegmentErrorCodes.INVALID_TRANSITION,
          "segment " + id + " is " + status + "; cannot become " + to);
    }
    status = to;
    updatedAt = now;
  }

  private static String validName(String value) {
    if (value == null || value.isBlank() || value.length() > MAX_NAME) {
      throw new DomainRuleViolation(
          SegmentErrorCodes.INVALID_DETAILS, "name must be 1-" + MAX_NAME + " characters");
    }
    return value;
  }

  private static String validDescription(String value) {
    if (value != null && value.length() > MAX_DESCRIPTION) {
      throw new DomainRuleViolation(
          SegmentErrorCodes.INVALID_DETAILS,
          "description exceeds " + MAX_DESCRIPTION + " characters");
    }
    return value;
  }

  public SegmentId id() {
    return id;
  }

  public TenantId tenantId() {
    return tenantId;
  }

  public String name() {
    return name;
  }

  public Optional<String> description() {
    return Optional.ofNullable(description);
  }

  public SegmentDefinition definition() {
    return definition;
  }

  public int definitionVersion() {
    return definitionVersion;
  }

  public SegmentStatus status() {
    return status;
  }

  public SegmentOrigin origin() {
    return origin;
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
