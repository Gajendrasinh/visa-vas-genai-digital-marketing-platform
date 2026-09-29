package com.vasmarketing.segment.application;

import com.vasmarketing.segment.domain.model.SegmentOrigin;
import com.vasmarketing.segment.domain.model.SegmentStatus;
import java.time.Instant;
import java.util.UUID;

public record SegmentSummary(
    UUID id,
    String name,
    SegmentStatus status,
    SegmentOrigin origin,
    int definitionVersion,
    Instant createdAt,
    long version) {}
