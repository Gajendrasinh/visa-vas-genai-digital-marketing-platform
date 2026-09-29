package com.vasmarketing.segment.domain.model;

import com.vasmarketing.platform.types.UuidV7;
import java.util.Objects;
import java.util.UUID;

public record SegmentId(UUID value) {

  public SegmentId {
    Objects.requireNonNull(value, "segment id");
  }

  public static SegmentId newId() {
    return new SegmentId(UuidV7.generate());
  }

  @Override
  public String toString() {
    return value.toString();
  }
}
