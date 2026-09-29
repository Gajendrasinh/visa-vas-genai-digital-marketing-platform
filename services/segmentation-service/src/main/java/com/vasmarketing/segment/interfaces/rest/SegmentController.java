package com.vasmarketing.segment.interfaces.rest;

import static com.vasmarketing.platform.security.Permissions.SEGMENT_READ;
import static com.vasmarketing.platform.security.Permissions.SEGMENT_WRITE;

import com.vasmarketing.platform.idempotency.Idempotent;
import com.vasmarketing.platform.types.Actor;
import com.vasmarketing.platform.web.http.EntityTags;
import com.vasmarketing.segment.application.SegmentService;
import com.vasmarketing.segment.domain.model.Segment;
import com.vasmarketing.segment.domain.model.SegmentId;
import com.vasmarketing.segment.domain.model.SegmentStatus;
import com.vasmarketing.segment.interfaces.rest.SegmentDtos.SegmentPage;
import com.vasmarketing.segment.interfaces.rest.SegmentDtos.SegmentRequest;
import com.vasmarketing.segment.interfaces.rest.SegmentDtos.SegmentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/segments")
@Tag(name = "Segments", description = "Segment definitions over allow-listed customer features")
class SegmentController {

  private final SegmentService segments;

  SegmentController(SegmentService segments) {
    this.segments = segments;
  }

  @PostMapping
  @Idempotent
  @PreAuthorize("hasAuthority('" + SEGMENT_WRITE + "')")
  @Operation(summary = "Create a DRAFT segment")
  ResponseEntity<SegmentResponse> create(Actor actor, @Valid @RequestBody SegmentRequest request) {
    Segment segment =
        segments.create(
            actor,
            request.name(),
            request.description(),
            request.toDefinition(),
            request.originOrManual());
    return ResponseEntity.created(URI.create("/api/v1/segments/" + segment.id()))
        .eTag(EntityTags.of(segment.version().orElseThrow()))
        .body(SegmentResponse.of(segment));
  }

  @GetMapping
  @PreAuthorize("hasAuthority('" + SEGMENT_READ + "')")
  @Operation(summary = "List segments, newest first")
  SegmentPage list(
      Actor actor,
      @RequestParam(required = false) SegmentStatus status,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "20") int limit) {
    return SegmentPage.of(segments.list(actor, status, cursor, limit));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('" + SEGMENT_READ + "')")
  @Operation(summary = "Get a segment and its definition")
  ResponseEntity<SegmentResponse> get(Actor actor, @PathVariable UUID id) {
    return ok(segments.get(actor, new SegmentId(id)));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAuthority('" + SEGMENT_WRITE + "')")
  @Operation(summary = "Replace name, description and (DRAFT only) definition; If-Match required")
  ResponseEntity<SegmentResponse> update(
      Actor actor,
      @PathVariable UUID id,
      @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch,
      @Valid @RequestBody SegmentRequest request) {
    return ok(
        segments.update(
            actor,
            new SegmentId(id),
            request.name(),
            request.description(),
            request.toDefinition(),
            EntityTags.required(ifMatch)));
  }

  @PostMapping("/{id}/activate")
  @PreAuthorize("hasAuthority('" + SEGMENT_WRITE + "')")
  @Operation(summary = "Activate a segment: its definition is frozen and membership is computed")
  ResponseEntity<SegmentResponse> activate(
      Actor actor,
      @PathVariable UUID id,
      @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
    return ok(segments.activate(actor, new SegmentId(id), EntityTags.optional(ifMatch)));
  }

  @PostMapping("/{id}/retire")
  @PreAuthorize("hasAuthority('" + SEGMENT_WRITE + "')")
  @Operation(summary = "Retire an active segment")
  ResponseEntity<SegmentResponse> retire(
      Actor actor,
      @PathVariable UUID id,
      @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
    return ok(segments.retire(actor, new SegmentId(id), EntityTags.optional(ifMatch)));
  }

  private static ResponseEntity<SegmentResponse> ok(Segment segment) {
    return ResponseEntity.ok()
        .eTag(EntityTags.of(segment.version().orElseThrow()))
        .body(SegmentResponse.of(segment));
  }
}
