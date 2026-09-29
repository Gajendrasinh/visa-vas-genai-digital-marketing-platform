package com.vasmarketing.customer.interfaces.rest;

import static com.vasmarketing.platform.security.Permissions.CUSTOMER_PROFILE_READ;
import static com.vasmarketing.platform.security.Permissions.CUSTOMER_WRITE;

import com.vasmarketing.customer.application.CustomerService;
import com.vasmarketing.customer.application.RegisterCustomerCommand;
import com.vasmarketing.customer.domain.customer.Customer;
import com.vasmarketing.customer.domain.customer.CustomerId;
import com.vasmarketing.customer.interfaces.rest.CustomerDtos.ConsentRequest;
import com.vasmarketing.customer.interfaces.rest.CustomerDtos.CustomerProfileResponse;
import com.vasmarketing.customer.interfaces.rest.CustomerDtos.CustomerResponse;
import com.vasmarketing.customer.interfaces.rest.CustomerDtos.PersonalDataRequest;
import com.vasmarketing.customer.interfaces.rest.CustomerDtos.PreferencesDto;
import com.vasmarketing.customer.interfaces.rest.CustomerDtos.RegisterCustomerRequest;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customers")
@Tag(name = "Customers", description = "Customer records (PII masked), consent and preferences")
class CustomerController {

  private final CustomerService customers;

  CustomerController(CustomerService customers) {
    this.customers = customers;
  }

  @PostMapping
  @Idempotent
  @PreAuthorize("hasAuthority('" + CUSTOMER_WRITE + "')")
  @Operation(summary = "Register a customer (no marketing consent until explicitly granted)")
  ResponseEntity<CustomerResponse> register(
      Actor actor, @Valid @RequestBody RegisterCustomerRequest request) {
    Customer customer =
        customers.register(
            actor,
            new RegisterCustomerCommand(
                request.personalData().toPersonalData(),
                request.countryCode(),
                request.birthYear(),
                request.preferences().toPreferences()));
    return ResponseEntity.created(URI.create("/api/v1/customers/" + customer.id()))
        .eTag(EntityTags.of(customer.version().orElseThrow()))
        .body(CustomerResponse.of(customer));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('" + CUSTOMER_PROFILE_READ + "')")
  @Operation(summary = "Get a customer with PII masked")
  ResponseEntity<CustomerResponse> get(Actor actor, @PathVariable UUID id) {
    return ok(customers.get(actor, new CustomerId(id)));
  }

  @GetMapping("/{id}/profile")
  @PreAuthorize("hasAuthority('" + CUSTOMER_PROFILE_READ + "')")
  @Operation(summary = "Get the marketing profile (no PII)")
  CustomerProfileResponse profile(Actor actor, @PathVariable UUID id) {
    return CustomerProfileResponse.of(customers.get(actor, new CustomerId(id)));
  }

  @PutMapping("/{id}/contact")
  @PreAuthorize("hasAuthority('" + CUSTOMER_WRITE + "')")
  @Operation(summary = "Replace contact data (If-Match required)")
  ResponseEntity<CustomerResponse> updateContact(
      Actor actor,
      @PathVariable UUID id,
      @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch,
      @Valid @RequestBody PersonalDataRequest request) {
    return ok(
        customers.updateContact(
            actor, new CustomerId(id), request.toPersonalData(), EntityTags.required(ifMatch)));
  }

  @PutMapping("/{id}/preferences")
  @PreAuthorize("hasAuthority('" + CUSTOMER_WRITE + "')")
  @Operation(summary = "Replace channel opt-ins and interests (If-Match required)")
  ResponseEntity<CustomerResponse> updatePreferences(
      Actor actor,
      @PathVariable UUID id,
      @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch,
      @Valid @RequestBody PreferencesDto request) {
    return ok(
        customers.updatePreferences(
            actor, new CustomerId(id), request.toPreferences(), EntityTags.required(ifMatch)));
  }

  @PostMapping("/{id}/consent")
  @PreAuthorize("hasAuthority('" + CUSTOMER_WRITE + "')")
  @Operation(summary = "Grant or withdraw marketing consent")
  ResponseEntity<CustomerResponse> consent(
      Actor actor,
      @PathVariable UUID id,
      @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch,
      @Valid @RequestBody ConsentRequest request) {
    CustomerId customerId = new CustomerId(id);
    return ok(
        request.granted()
            ? customers.grantMarketingConsent(actor, customerId, EntityTags.optional(ifMatch))
            : customers.withdrawMarketingConsent(actor, customerId, EntityTags.optional(ifMatch)));
  }

  @PostMapping("/{id}/close")
  @PreAuthorize("hasAuthority('" + CUSTOMER_WRITE + "')")
  @Operation(summary = "Close a customer (also withdraws consent)")
  ResponseEntity<CustomerResponse> close(
      Actor actor,
      @PathVariable UUID id,
      @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
    return ok(customers.close(actor, new CustomerId(id), EntityTags.optional(ifMatch)));
  }

  private static ResponseEntity<CustomerResponse> ok(Customer customer) {
    return ResponseEntity.ok()
        .eTag(EntityTags.of(customer.version().orElseThrow()))
        .body(CustomerResponse.of(customer));
  }
}
