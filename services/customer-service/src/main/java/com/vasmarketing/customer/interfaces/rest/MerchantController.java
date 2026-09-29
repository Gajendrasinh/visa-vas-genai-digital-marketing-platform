package com.vasmarketing.customer.interfaces.rest;

import static com.vasmarketing.platform.security.Permissions.MERCHANT_WRITE;
import static com.vasmarketing.platform.security.Permissions.OFFER_READ;

import com.vasmarketing.customer.application.MerchantService;
import com.vasmarketing.customer.domain.merchant.Merchant;
import com.vasmarketing.customer.domain.merchant.MerchantId;
import com.vasmarketing.customer.interfaces.rest.CustomerDtos.MerchantRequest;
import com.vasmarketing.customer.interfaces.rest.CustomerDtos.MerchantResponse;
import com.vasmarketing.platform.idempotency.Idempotent;
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
import org.springframework.web.bind.annotation.RestController;

/** Network-wide merchant reference data; readable by anyone who can read offers. */
@RestController
@RequestMapping("/api/v1/merchants")
@Tag(name = "Merchants", description = "Merchant reference data and MCC-derived categories")
class MerchantController {

  private final MerchantService merchants;

  MerchantController(MerchantService merchants) {
    this.merchants = merchants;
  }

  @PostMapping
  @Idempotent
  @PreAuthorize("hasAuthority('" + MERCHANT_WRITE + "')")
  @Operation(summary = "Register a merchant; its spend category is derived from the MCC")
  ResponseEntity<MerchantResponse> register(@Valid @RequestBody MerchantRequest request) {
    Merchant merchant =
        merchants.register(request.name(), request.mcc(), request.countryCode(), request.city());
    return ResponseEntity.created(URI.create("/api/v1/merchants/" + merchant.id()))
        .eTag(EntityTags.of(merchant.version().orElseThrow()))
        .body(MerchantResponse.of(merchant));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('" + OFFER_READ + "')")
  @Operation(summary = "Get a merchant")
  ResponseEntity<MerchantResponse> get(@PathVariable UUID id) {
    return ok(merchants.get(new MerchantId(id)));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAuthority('" + MERCHANT_WRITE + "')")
  @Operation(summary = "Update a merchant (If-Match required)")
  ResponseEntity<MerchantResponse> update(
      @PathVariable UUID id,
      @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch,
      @Valid @RequestBody MerchantRequest request) {
    return ok(
        merchants.update(
            new MerchantId(id),
            request.name(),
            request.mcc(),
            request.city(),
            EntityTags.required(ifMatch)));
  }

  @PostMapping("/{id}/deactivate")
  @PreAuthorize("hasAuthority('" + MERCHANT_WRITE + "')")
  @Operation(summary = "Deactivate a merchant")
  ResponseEntity<MerchantResponse> deactivate(
      @PathVariable UUID id,
      @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
    return ok(merchants.deactivate(new MerchantId(id), EntityTags.optional(ifMatch)));
  }

  private static ResponseEntity<MerchantResponse> ok(Merchant merchant) {
    return ResponseEntity.ok()
        .eTag(EntityTags.of(merchant.version().orElseThrow()))
        .body(MerchantResponse.of(merchant));
  }
}
