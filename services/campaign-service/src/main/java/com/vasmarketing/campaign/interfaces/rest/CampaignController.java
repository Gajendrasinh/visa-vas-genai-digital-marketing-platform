package com.vasmarketing.campaign.interfaces.rest;

import static com.vasmarketing.platform.security.Permissions.CAMPAIGN_APPROVE;
import static com.vasmarketing.platform.security.Permissions.CAMPAIGN_DRAFT;
import static com.vasmarketing.platform.security.Permissions.CAMPAIGN_PUBLISH;
import static com.vasmarketing.platform.security.Permissions.CAMPAIGN_READ;
import static com.vasmarketing.platform.security.Permissions.CAMPAIGN_SUBMIT;

import com.vasmarketing.campaign.application.CampaignAuthoringService;
import com.vasmarketing.campaign.application.CampaignLifecycleService;
import com.vasmarketing.campaign.application.CampaignQueryService;
import com.vasmarketing.campaign.application.CreateCampaignCommand;
import com.vasmarketing.campaign.domain.model.ApprovalDecision;
import com.vasmarketing.campaign.domain.model.Campaign;
import com.vasmarketing.campaign.domain.model.CampaignId;
import com.vasmarketing.campaign.domain.model.CampaignStatus;
import com.vasmarketing.campaign.domain.model.VariantId;
import com.vasmarketing.campaign.interfaces.rest.CampaignRequests.AddVariantRequest;
import com.vasmarketing.campaign.interfaces.rest.CampaignRequests.ApprovalRequest;
import com.vasmarketing.campaign.interfaces.rest.CampaignRequests.CampaignDetailsRequest;
import com.vasmarketing.campaign.interfaces.rest.CampaignRequests.ComplianceReviewRequest;
import com.vasmarketing.campaign.interfaces.rest.CampaignResponses.CampaignPage;
import com.vasmarketing.campaign.interfaces.rest.CampaignResponses.CampaignResponse;
import com.vasmarketing.campaign.interfaces.rest.CampaignResponses.VariantCreated;
import com.vasmarketing.platform.idempotency.Idempotent;
import com.vasmarketing.platform.types.Actor;
import com.vasmarketing.platform.web.http.EntityTags;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Campaign API. Thin: translates HTTP to use cases; authorization is declared per operation and
 * re-checked by domain rules (e.g. four-eyes approval).
 */
@RestController
@RequestMapping("/api/v1/campaigns")
@Tag(name = "Campaigns", description = "Campaign lifecycle, content variants and approval")
class CampaignController {

  private static final String IF_MATCH = HttpHeaders.IF_MATCH;

  private final CampaignAuthoringService authoring;
  private final CampaignLifecycleService lifecycle;
  private final CampaignQueryService queries;

  CampaignController(
      CampaignAuthoringService authoring,
      CampaignLifecycleService lifecycle,
      CampaignQueryService queries) {
    this.authoring = authoring;
    this.lifecycle = lifecycle;
    this.queries = queries;
  }

  @PostMapping
  @Idempotent
  @PreAuthorize("hasAuthority('" + CAMPAIGN_DRAFT + "')")
  @Operation(summary = "Create a DRAFT campaign")
  ResponseEntity<CampaignResponse> create(
      Actor actor, @Valid @RequestBody CampaignDetailsRequest request) {
    Campaign campaign = authoring.create(actor, CreateCampaignCommand.manual(request.toDetails()));
    return ResponseEntity.created(URI.create("/api/v1/campaigns/" + campaign.id()))
        .eTag(EntityTags.of(campaign.version().orElseThrow()))
        .body(CampaignResponse.of(campaign));
  }

  @GetMapping
  @PreAuthorize("hasAuthority('" + CAMPAIGN_READ + "')")
  @Operation(summary = "List campaigns, newest first (keyset pagination)")
  CampaignPage list(
      Actor actor,
      @RequestParam(required = false) CampaignStatus status,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "20") int limit) {
    return CampaignPage.of(queries.list(actor, status, cursor, limit));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('" + CAMPAIGN_READ + "')")
  @Operation(summary = "Get a campaign with its variants and approvals")
  ResponseEntity<CampaignResponse> get(Actor actor, @PathVariable UUID id) {
    return ok(queries.get(actor, new CampaignId(id)));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAuthority('" + CAMPAIGN_DRAFT + "')")
  @Operation(summary = "Replace the details of a DRAFT campaign (If-Match required)")
  ResponseEntity<CampaignResponse> update(
      Actor actor,
      @PathVariable UUID id,
      @RequestHeader(name = IF_MATCH, required = false) String ifMatch,
      @Valid @RequestBody CampaignDetailsRequest request) {
    return ok(
        authoring.updateDetails(
            actor, new CampaignId(id), request.toDetails(), EntityTags.required(ifMatch)));
  }

  @PostMapping("/{id}/variants")
  @Idempotent
  @PreAuthorize("hasAuthority('" + CAMPAIGN_DRAFT + "')")
  @Operation(summary = "Add a content variant to a DRAFT campaign")
  ResponseEntity<VariantCreated> addVariant(
      Actor actor,
      @PathVariable UUID id,
      @RequestHeader(name = IF_MATCH, required = false) String ifMatch,
      @Valid @RequestBody AddVariantRequest request) {
    CampaignId campaignId = new CampaignId(id);
    VariantId variantId =
        authoring.addVariant(
            actor,
            campaignId,
            request.toContent(),
            request.generatedBy(),
            request.toPromptReference(),
            EntityTags.optional(ifMatch));
    Campaign campaign = queries.get(actor, campaignId);
    return ResponseEntity.created(
            URI.create("/api/v1/campaigns/" + id + "/variants/" + variantId.value()))
        .eTag(EntityTags.of(campaign.version().orElseThrow()))
        .body(new VariantCreated(variantId.value(), CampaignResponse.of(campaign)));
  }

  @DeleteMapping("/{id}/variants/{variantId}")
  @PreAuthorize("hasAuthority('" + CAMPAIGN_DRAFT + "')")
  @Operation(summary = "Remove a content variant from a DRAFT campaign")
  ResponseEntity<CampaignResponse> removeVariant(
      Actor actor,
      @PathVariable UUID id,
      @PathVariable UUID variantId,
      @RequestHeader(name = IF_MATCH, required = false) String ifMatch) {
    return ok(
        authoring.removeVariant(
            actor, new CampaignId(id), new VariantId(variantId), EntityTags.optional(ifMatch)));
  }

  @PostMapping("/{id}/variants/{variantId}/compliance-review")
  @PreAuthorize("hasAuthority('" + CAMPAIGN_DRAFT + "')")
  @Operation(summary = "Record the compliance review result of a variant")
  ResponseEntity<CampaignResponse> recordCompliance(
      Actor actor,
      @PathVariable UUID id,
      @PathVariable UUID variantId,
      @RequestHeader(name = IF_MATCH, required = false) String ifMatch,
      @Valid @RequestBody ComplianceReviewRequest request) {
    return ok(
        authoring.recordCompliance(
            actor,
            new CampaignId(id),
            new VariantId(variantId),
            request.passed(),
            request.report(),
            EntityTags.optional(ifMatch)));
  }

  @PostMapping("/{id}/submit")
  @PreAuthorize("hasAuthority('" + CAMPAIGN_SUBMIT + "')")
  @Operation(summary = "Submit a DRAFT campaign for approval")
  ResponseEntity<CampaignResponse> submit(
      Actor actor,
      @PathVariable UUID id,
      @RequestHeader(name = IF_MATCH, required = false) String ifMatch) {
    return ok(lifecycle.submit(actor, new CampaignId(id), EntityTags.optional(ifMatch)));
  }

  @PostMapping("/{id}/approvals")
  @PreAuthorize("hasAuthority('" + CAMPAIGN_APPROVE + "')")
  @Operation(summary = "Approve or reject a pending campaign (approver must differ from creator)")
  ResponseEntity<CampaignResponse> decide(
      Actor actor,
      @PathVariable UUID id,
      @RequestHeader(name = IF_MATCH, required = false) String ifMatch,
      @Valid @RequestBody ApprovalRequest request) {
    CampaignId campaignId = new CampaignId(id);
    Campaign campaign =
        request.decision() == ApprovalDecision.APPROVE
            ? lifecycle.approve(actor, campaignId, request.comment(), EntityTags.optional(ifMatch))
            : lifecycle.reject(actor, campaignId, request.comment(), EntityTags.optional(ifMatch));
    return ok(campaign);
  }

  @PostMapping("/{id}/publish")
  @Idempotent
  @PreAuthorize("hasAuthority('" + CAMPAIGN_PUBLISH + "')")
  @Operation(summary = "Publish an approved campaign (ACTIVE now or SCHEDULED)")
  ResponseEntity<CampaignResponse> publish(
      Actor actor,
      @PathVariable UUID id,
      @RequestHeader(name = IF_MATCH, required = false) String ifMatch) {
    return ok(lifecycle.publish(actor, new CampaignId(id), EntityTags.optional(ifMatch)));
  }

  @PostMapping("/{id}/pause")
  @PreAuthorize("hasAuthority('" + CAMPAIGN_PUBLISH + "')")
  @Operation(summary = "Pause an active campaign")
  ResponseEntity<CampaignResponse> pause(
      Actor actor,
      @PathVariable UUID id,
      @RequestHeader(name = IF_MATCH, required = false) String ifMatch) {
    return ok(lifecycle.pause(actor, new CampaignId(id), EntityTags.optional(ifMatch)));
  }

  @PostMapping("/{id}/resume")
  @PreAuthorize("hasAuthority('" + CAMPAIGN_PUBLISH + "')")
  @Operation(summary = "Resume a paused campaign")
  ResponseEntity<CampaignResponse> resume(
      Actor actor,
      @PathVariable UUID id,
      @RequestHeader(name = IF_MATCH, required = false) String ifMatch) {
    return ok(lifecycle.resume(actor, new CampaignId(id), EntityTags.optional(ifMatch)));
  }

  @PostMapping("/{id}/archive")
  @PreAuthorize("hasAuthority('" + CAMPAIGN_PUBLISH + "')")
  @Operation(summary = "Archive a DRAFT or COMPLETED campaign")
  ResponseEntity<CampaignResponse> archive(
      Actor actor,
      @PathVariable UUID id,
      @RequestHeader(name = IF_MATCH, required = false) String ifMatch) {
    return ok(lifecycle.archive(actor, new CampaignId(id), EntityTags.optional(ifMatch)));
  }

  private static ResponseEntity<CampaignResponse> ok(Campaign campaign) {
    return ResponseEntity.ok()
        .eTag(EntityTags.of(campaign.version().orElseThrow()))
        .body(CampaignResponse.of(campaign));
  }
}
