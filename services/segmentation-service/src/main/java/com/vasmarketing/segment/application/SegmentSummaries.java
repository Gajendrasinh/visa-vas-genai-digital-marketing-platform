package com.vasmarketing.segment.application;

import com.vasmarketing.platform.types.TenantId;
import com.vasmarketing.segment.domain.model.SegmentStatus;
import java.util.List;
import java.util.UUID;

/** Read-model port for segment listings (newest first, keyset-paginated). */
public interface SegmentSummaries {

  List<SegmentSummary> page(TenantId tenantId, SegmentStatus status, UUID before, int fetchSize);
}
