package com.vasmarketing.segment.domain.port;

import com.vasmarketing.platform.types.TenantId;
import com.vasmarketing.segment.domain.model.Segment;
import com.vasmarketing.segment.domain.model.SegmentId;
import java.util.Optional;

public interface SegmentRepository {

  Optional<Segment> findById(TenantId tenantId, SegmentId id);

  boolean existsByName(TenantId tenantId, String name);

  /** Inserts or updates; fails with a concurrency conflict on a stale version. */
  Segment save(Segment segment);
}
