package com.vasmarketing.segment.application;

import com.vasmarketing.platform.types.Actor;
import com.vasmarketing.platform.types.DomainRuleViolation;
import com.vasmarketing.platform.types.ResourceNotFound;
import com.vasmarketing.segment.domain.criteria.SegmentDefinition;
import com.vasmarketing.segment.domain.criteria.SegmentErrorCodes;
import com.vasmarketing.segment.domain.model.Segment;
import com.vasmarketing.segment.domain.model.SegmentId;
import com.vasmarketing.segment.domain.model.SegmentOrigin;
import com.vasmarketing.segment.domain.port.SegmentRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.function.BiConsumer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Use cases for segment definitions and their lifecycle. */
@Service
@Transactional
public class SegmentService {

  private final SegmentRepository repository;
  private final Clock clock;

  SegmentService(SegmentRepository repository, Clock clock) {
    this.repository = repository;
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

  public Segment describe(Actor actor, SegmentId id, String name, String description) {
    Segment segment = load(actor, id);
    if (!segment.name().equals(name)) {
      requireUniqueName(actor, name);
    }
    segment.describe(name, description, clock.instant());
    return repository.save(segment);
  }

  public Segment redefine(Actor actor, SegmentId id, SegmentDefinition definition) {
    return apply(actor, id, (segment, now) -> segment.redefine(definition, now));
  }

  public Segment activate(Actor actor, SegmentId id) {
    return apply(actor, id, (segment, now) -> segment.activate(now));
  }

  public Segment retire(Actor actor, SegmentId id) {
    return apply(actor, id, (segment, now) -> segment.retire(now));
  }

  @Transactional(readOnly = true)
  public Segment get(Actor actor, SegmentId id) {
    return load(actor, id);
  }

  private Segment apply(Actor actor, SegmentId id, BiConsumer<Segment, Instant> change) {
    Segment segment = load(actor, id);
    change.accept(segment, clock.instant());
    return repository.save(segment);
  }

  private Segment load(Actor actor, SegmentId id) {
    return repository
        .findById(actor.tenantId(), id)
        .orElseThrow(() -> new ResourceNotFound("Segment", id));
  }

  private void requireUniqueName(Actor actor, String name) {
    if (repository.existsByName(actor.tenantId(), name)) {
      throw new DomainRuleViolation(
          SegmentErrorCodes.DUPLICATE_NAME, "a segment named '" + name + "' already exists");
    }
  }
}
