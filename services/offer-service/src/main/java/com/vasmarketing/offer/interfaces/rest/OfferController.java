package com.vasmarketing.offer.interfaces.rest;

import static com.vasmarketing.platform.security.Permissions.OFFER_READ;
import static com.vasmarketing.platform.security.Permissions.OFFER_WRITE;

import com.vasmarketing.offer.application.OfferManagementService;
import com.vasmarketing.offer.domain.model.Offer;
import com.vasmarketing.offer.domain.model.OfferCategory;
import com.vasmarketing.offer.domain.model.OfferId;
import com.vasmarketing.offer.domain.model.OfferStatus;
import com.vasmarketing.offer.interfaces.rest.OfferDtos.CreateOfferRequest;
import com.vasmarketing.offer.interfaces.rest.OfferDtos.OfferPage;
import com.vasmarketing.offer.interfaces.rest.OfferDtos.OfferResponse;
import com.vasmarketing.offer.interfaces.rest.OfferDtos.OfferTermsRequest;
import com.vasmarketing.offer.interfaces.rest.OfferDtos.ReplaceRulesRequest;
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
@RequestMapping("/api/v1/offers")
@Tag(name = "Offers", description = "Offer catalogue and deterministic eligibility rules")
class OfferController {

  private final OfferManagementService offers;

  OfferController(OfferManagementService offers) {
    this.offers = offers;
  }

  @PostMapping
  @Idempotent
  @PreAuthorize("hasAuthority('" + OFFER_WRITE + "')")
  @Operation(summary = "Create a DRAFT offer with its eligibility rules")
  ResponseEntity<OfferResponse> create(
      Actor actor, @Valid @RequestBody CreateOfferRequest request) {
    Offer offer = offers.create(actor, request.terms().toTerms(), request.toRules());
    return ResponseEntity.created(URI.create("/api/v1/offers/" + offer.id()))
        .eTag(EntityTags.of(offer.version().orElseThrow()))
        .body(OfferResponse.of(offer));
  }

  @GetMapping
  @PreAuthorize("hasAuthority('" + OFFER_READ + "')")
  @Operation(summary = "List offers, newest first")
  OfferPage list(
      Actor actor,
      @RequestParam(required = false) OfferStatus status,
      @RequestParam(required = false) OfferCategory category,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "20") int limit) {
    return OfferPage.of(offers.list(actor, status, category, cursor, limit));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('" + OFFER_READ + "')")
  @Operation(summary = "Get an offer with its rules")
  ResponseEntity<OfferResponse> get(Actor actor, @PathVariable UUID id) {
    return ok(offers.get(actor, new OfferId(id)));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAuthority('" + OFFER_WRITE + "')")
  @Operation(summary = "Replace the terms of a DRAFT or PAUSED offer (If-Match required)")
  ResponseEntity<OfferResponse> updateTerms(
      Actor actor,
      @PathVariable UUID id,
      @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch,
      @Valid @RequestBody OfferTermsRequest request) {
    return ok(
        offers.updateTerms(
            actor, new OfferId(id), request.toTerms(), EntityTags.required(ifMatch)));
  }

  @PutMapping("/{id}/rules")
  @PreAuthorize("hasAuthority('" + OFFER_WRITE + "')")
  @Operation(
      summary = "Replace the eligibility rules (bumps the rule-set version; If-Match required)")
  ResponseEntity<OfferResponse> replaceRules(
      Actor actor,
      @PathVariable UUID id,
      @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch,
      @Valid @RequestBody ReplaceRulesRequest request) {
    return ok(
        offers.replaceRules(
            actor, new OfferId(id), request.toRules(), EntityTags.required(ifMatch)));
  }

  @PostMapping("/{id}/activate")
  @PreAuthorize("hasAuthority('" + OFFER_WRITE + "')")
  @Operation(summary = "Activate an offer (requires a marketing-consent rule)")
  ResponseEntity<OfferResponse> activate(
      Actor actor,
      @PathVariable UUID id,
      @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
    return ok(offers.activate(actor, new OfferId(id), EntityTags.optional(ifMatch)));
  }

  @PostMapping("/{id}/pause")
  @PreAuthorize("hasAuthority('" + OFFER_WRITE + "')")
  @Operation(summary = "Pause an active offer")
  ResponseEntity<OfferResponse> pause(
      Actor actor,
      @PathVariable UUID id,
      @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
    return ok(offers.pause(actor, new OfferId(id), EntityTags.optional(ifMatch)));
  }

  private static ResponseEntity<OfferResponse> ok(Offer offer) {
    return ResponseEntity.ok()
        .eTag(EntityTags.of(offer.version().orElseThrow()))
        .body(OfferResponse.of(offer));
  }
}
