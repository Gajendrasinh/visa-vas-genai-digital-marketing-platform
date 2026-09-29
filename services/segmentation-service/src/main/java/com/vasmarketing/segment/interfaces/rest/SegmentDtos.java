package com.vasmarketing.segment.interfaces.rest;

import com.vasmarketing.platform.types.CursorPage;
import com.vasmarketing.segment.application.SegmentSummary;
import com.vasmarketing.segment.application.dsl.SegmentDefinitionJson;
import com.vasmarketing.segment.domain.criteria.SegmentDefinition;
import com.vasmarketing.segment.domain.model.Segment;
import com.vasmarketing.segment.domain.model.SegmentOrigin;
import com.vasmarketing.segment.domain.model.SegmentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

final class SegmentDtos {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private SegmentDtos() {}

  @Schema(
      description = "Segment rule tree",
      example = "{\"all\":[{\"feature\":\"travel_txn_count_90d\",\"op\":\"gte\",\"value\":3}]}")
  record SegmentRequest(
      @NotBlank @Size(max = 100) String name,
      @Size(max = 500) String description,
      @NotNull JsonNode definition,
      SegmentOrigin origin) {

    SegmentDefinition toDefinition() {
      return SegmentDefinitionJson.parse(JSON.writeValueAsString(definition));
    }

    SegmentOrigin originOrManual() {
      return origin == null ? SegmentOrigin.MANUAL : origin;
    }
  }

  record SegmentResponse(
      UUID id,
      String name,
      String description,
      JsonNode definition,
      int definitionVersion,
      SegmentStatus status,
      SegmentOrigin origin,
      String createdBy,
      Instant createdAt,
      Instant updatedAt,
      long version) {

    static SegmentResponse of(Segment s) {
      return new SegmentResponse(
          s.id().value(),
          s.name(),
          s.description().orElse(null),
          JSON.readTree(SegmentDefinitionJson.write(s.definition())),
          s.definitionVersion(),
          s.status(),
          s.origin(),
          s.createdBy().value(),
          s.createdAt(),
          s.updatedAt(),
          s.version().orElseThrow());
    }
  }

  record SegmentPage(List<SegmentSummary> items, String nextCursor) {
    static SegmentPage of(CursorPage<SegmentSummary> page) {
      return new SegmentPage(page.items(), page.nextCursor().orElse(null));
    }
  }
}
