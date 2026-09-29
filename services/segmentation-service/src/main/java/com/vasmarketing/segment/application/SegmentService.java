package com.vasmarketing.segment.application;

import com.vasmarketing.platform.types.Actor;
import com.vasmarketing.platform.types.CursorPage;
import com.vasmarketing.platform.types.DomainRuleViolation;
import com.vasmarketing.platform.types.Precondition;
import com.vasmarketing.platform.types.ResourceNotFound;
import com.vasmarketing.segment.domain.criteria.SegmentDefinition;
import com.vasmarketing.segment.domain.criteria.SegmentErrorCodes;
import com.vasmarketing.segment.domain.model.Segment;
import com.vasmarketing.segment.domain.model.SegmentId;
import com.vasmarketing.segment.domain.model.SegmentOrigin;
import com.vasmarketing.segment.domain.model.SegmentStatus;
import com.vasmarketing.segment.domain.port.SegmentRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.function.BiConsumer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Use cases for segment definitions and their lifecycle. */
@Service
@Transactional
public class SegmentService {

  private final SegmentRepository repository;
  private final SegmentSummaries summaries;
  private final Clock clock;

  SegmentService(SegmentRepository repository, SegmentSummaries summaries, Clock clock) {
    this.repository = repository;
    this.summaries = summaries;
    this.clock = clock;
  }

  public Segment create(
      Actor actor,
      String name,
      String description,
      SegmentDefinition definition,
      SegmentOrigin origin) {
    requireUniqueName(actor, name);
    return repository.save(
        Segment.create(actor, name, description, definition, origin, clock.instant()));
  }

  /**
   * Replaces name, description and definition. The definition may only change while DRAFT; an
   * unchanged definition is accepted in any state so that renaming stays possible.
   */
  public Segment update(
      Actor actor,
      SegmentId id,
      String name,
      String description,
      SegmentDefinition definition,
      Precondition precondition) {
    Segment segment = loadForUpdate(actor, id, precondition);
    if (!segment.name().equals(name)) {
      requireUniqueName(actor, name);
    }
    Instant now = clock.instant();
    segment.describe(name, description, now);
    if (!segment.definition().equals(definition)) {
      segment.redefine(definition, now);
    }
    return repository.save(segment);
  }

  public Segment activate(Actor actor, SegmentId id, Precondition precondition) {
    return apply(actor, id, precondition, (segment, now) -> segment.activate(now));
  }

  public Segment retire(Actor actor, SegmentId id, Precondition precondition) {
    return apply(actor, id, precondition, (segment, now) -> segment.retire(now));
  }

  @Transactional(readOnly = true)
  public CursorPage<SegmentSummary> list(
      Actor actor, SegmentStatus status, String cursor, int limit) {
    int size = CursorPage.limit(limit);
    UUID before = cursor == null ? null : CursorPage.decode(cursor);
    return CursorPage.fromOverfetch(
        summaries.page(actor.tenantId(), status, before, size + 1), size, SegmentSummary::id);
  }

  @Transactional(readOnly = true)
  public Segment get(Actor actor, SegmentId id) {
    return load(actor, id);
  }

  private Segment apply(
      Actor actor, SegmentId id, Precondition precondition, BiConsumer<Segment, Instant> change) {
    Segment segment = loadForUpdate(actor, id, precondition);
    change.accept(segment, clock.instant());
    return repository.save(segment);
  }

  private Segment load(Actor actor, SegmentId id) {
    return repository
        .findById(actor.tenantId(), id)
        .orElseThrow(() -> new ResourceNotFound("Segment", id));
  }

  private Segment loadForUpdate(Actor actor, SegmentId id, Precondition precondition) {
    Segment segment = load(actor, id);
    precondition.check(segment.version());
    return segment;
  }

  private void requireUniqueName(Actor actor, String name) {
    if (repository.existsByName(actor.tenantId(), name)) {
      throw new DomainRuleViolation(
          SegmentErrorCodes.DUPLICATE_NAME, "a segment named '" + name + "' already exists");
    }
  }
}
