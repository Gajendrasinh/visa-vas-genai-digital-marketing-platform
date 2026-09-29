package com.vasmarketing.segment.infrastructure.persistence;

import com.vasmarketing.segment.domain.model.SegmentOrigin;
import com.vasmarketing.segment.domain.model.SegmentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "segments")
class SegmentEntity {

  @Id UUID id;

  @Column(name = "tenant_id", updatable = false)
  UUID tenantId;

  String name;
  String description;

  @JdbcTypeCode(SqlTypes.JSON)
  String definition;

  @Column(name = "definition_version")
  int definitionVersion;

  @Enumerated(EnumType.STRING)
  SegmentStatus status;

  @Enumerated(EnumType.STRING)
  @Column(updatable = false)
  SegmentOrigin origin;

  @Column(name = "created_by", updatable = false)
  String createdBy;

  @Column(name = "created_at", updatable = false)
  Instant createdAt;

  @Column(name = "updated_at")
  Instant updatedAt;

  @Version Long version;
}
