package com.vasmarketing.customer.interfaces.rest;

import com.vasmarketing.customer.domain.customer.Customer;
import com.vasmarketing.customer.domain.customer.CustomerStatus;
import com.vasmarketing.customer.domain.customer.PersonalData;
import com.vasmarketing.customer.domain.customer.Preferences;
import com.vasmarketing.customer.domain.customer.SpendCategory;
import com.vasmarketing.customer.domain.merchant.Merchant;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Request/response bodies of the customer and merchant APIs. */
final class CustomerDtos {

  private CustomerDtos() {}

  record PersonalDataRequest(
      @NotBlank @Size(max = 200) String fullName,
      @NotBlank @Email @Size(max = 254) String email,
      @Pattern(regexp = "^\\+[1-9][0-9]{7,14}$") String phone) {

    PersonalData toPersonalData() {
      return new PersonalData(fullName, email, phone);
    }
  }

  record PreferencesDto(
      boolean emailOptIn,
      boolean pushOptIn,
      boolean smsOptIn,
      @NotNull Set<@NotNull SpendCategory> preferredCategories,
      @NotBlank @Size(max = 5) String language) {

    Preferences toPreferences() {
      return new Preferences(emailOptIn, pushOptIn, smsOptIn, preferredCategories, language);
    }

    static PreferencesDto of(Preferences p) {
      return new PreferencesDto(
          p.emailOptIn(), p.pushOptIn(), p.smsOptIn(), p.preferredCategories(), p.language());
    }
  }

  record RegisterCustomerRequest(
      @NotNull @Valid PersonalDataRequest personalData,
      @NotNull @Pattern(regexp = "^[A-Z]{2}$") String countryCode,
      @Min(1900) @Max(2100) int birthYear,
      @NotNull @Valid PreferencesDto preferences) {}

  record ConsentRequest(@NotNull Boolean granted) {}

  /** Customer with PII masked; unmasked contact data is never returned by human-facing APIs. */
  record CustomerResponse(
      UUID id,
      PersonalData.MaskedPersonalData personalData,
      String countryCode,
      int birthYear,
      PreferencesDto preferences,
      boolean marketingConsent,
      Instant consentUpdatedAt,
      CustomerStatus status,
      Instant createdAt,
      Instant updatedAt,
      long version) {

    static CustomerResponse of(Customer c) {
      return new CustomerResponse(
          c.id().value(),
          c.personalData().masked(),
          c.countryCode(),
          c.birthYear(),
          PreferencesDto.of(c.preferences()),
          c.marketingConsent(),
          c.consentUpdatedAt().orElse(null),
          c.status(),
          c.createdAt(),
          c.updatedAt(),
          c.version().orElseThrow());
    }
  }

  /** Marketing profile without any PII; features join with the profile consumer (Phase 6). */
  record CustomerProfileResponse(
      UUID id,
      String countryCode,
      int birthYear,
      PreferencesDto preferences,
      boolean marketingConsent,
      List<Customer.Channel> contactableChannels,
      CustomerStatus status,
      long version) {

    static CustomerProfileResponse of(Customer c) {
      return new CustomerProfileResponse(
          c.id().value(),
          c.countryCode(),
          c.birthYear(),
          PreferencesDto.of(c.preferences()),
          c.marketingConsent(),
          Arrays.stream(Customer.Channel.values()).filter(c::isContactableBy).toList(),
          c.status(),
          c.version().orElseThrow());
    }
  }

  record MerchantRequest(
      @NotBlank @Size(max = 150) String name,
      @NotNull @Pattern(regexp = "^[0-9]{4}$") String mcc,
      @NotNull @Pattern(regexp = "^[A-Z]{2}$") String countryCode,
      @Size(max = 100) String city) {}

  record MerchantResponse(
      UUID id,
      String name,
      String mcc,
      SpendCategory category,
      String countryCode,
      String city,
      boolean active,
      long version) {

    static MerchantResponse of(Merchant m) {
      return new MerchantResponse(
          m.id().value(),
          m.name(),
          m.mcc().value(),
          m.category(),
          m.countryCode(),
          m.city().orElse(null),
          m.active(),
          m.version().orElseThrow());
    }
  }
}
