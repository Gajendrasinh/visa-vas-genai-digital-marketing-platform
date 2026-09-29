package com.vasmarketing.segment.infrastructure.persistence;

import com.vasmarketing.platform.types.DomainRuleViolation;
import com.vasmarketing.platform.types.TenantId;
import com.vasmarketing.platform.types.UserId;
import com.vasmarketing.segment.application.dsl.SegmentDefinitionJson;
import com.vasmarketing.segment.domain.criteria.SegmentErrorCodes;
import com.vasmarketing.segment.domain.model.Segment;
import com.vasmarketing.segment.domain.model.SegmentId;
import com.vasmarketing.segment.domain.port.SegmentRepository;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class JpaSegmentRepository implements SegmentRepository {

  private static final String NAME_CONSTRAINT = "segments_tenant_name_uk";

  private final SegmentJpaRepository jpa;

  JpaSegmentRepository(SegmentJpaRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Segment> findById(TenantId tenantId, SegmentId id) {
    return jpa.findByIdAndTenantId(id.value(), tenantId.value())
        .map(JpaSegmentRepository::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public boolean existsByName(TenantId tenantId, String name) {
    return jpa.existsByTenantIdAndName(tenantId.value(), name);
  }

  @Override
  @Transactional
  public Segment save(Segment segment) {
    SegmentEntity entity =
        segment.version().map(v -> managedEntity(segment, v)).orElseGet(SegmentEntity::new);
    apply(segment, entity);
    try {
      return toDomain(jpa.saveAndFlush(entity));
    } catch (DataIntegrityViolationException e) {
      String cause = e.getMostSpecificCause().getMessage();
      if (cause != null && cause.contains(NAME_CONSTRAINT)) {
        throw new DomainRuleViolation(
            SegmentErrorCodes.DUPLICATE_NAME,
            "a segment named '" + segment.name() + "' already exists");
      }
      throw e;
    }
  }

  private SegmentEntity managedEntity(Segment segment, long expectedVersion) {
    SegmentEntity entity =
        jpa.findByIdAndTenantId(segment.id().value(), segment.tenantId().value())
            .orElseThrow(
                () ->
                    new ObjectOptimisticLockingFailureException(
                        SegmentEntity.class, segment.id().value()));
    if (entity.version != expectedVersion) {
      throw new ObjectOptimisticLockingFailureException(SegmentEntity.class, entity.id);
    }
    return entity;
  }

  private static void apply(Segment segment, SegmentEntity e) {
    e.id = segment.id().value();
    e.tenantId = segment.tenantId().value();
    e.name = segment.name();
    e.description = segment.description().orElse(null);
    e.definition = SegmentDefinitionJson.write(segment.definition());
    e.definitionVersion = segment.definitionVersion();
    e.status = segment.status();
    e.origin = segment.origin();
    e.createdBy = segment.createdBy().value();
    e.createdAt = segment.createdAt();
    e.updatedAt = segment.updatedAt();
  }

  private static Segment toDomain(SegmentEntity e) {
    return Segment.restore(
        new Segment.State(
            new SegmentId(e.id),
            new TenantId(e.tenantId),
            e.name,
            e.description,
            SegmentDefinitionJson.parse(e.definition),
            e.definitionVersion,
            e.status,
            e.origin,
            new UserId(e.createdBy),
            e.createdAt,
            e.updatedAt,
            e.version));
  }
}
