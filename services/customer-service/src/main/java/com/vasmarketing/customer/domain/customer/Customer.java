package com.vasmarketing.customer.domain.customer;

import com.vasmarketing.platform.types.DomainRuleViolation;
import com.vasmarketing.platform.types.TenantId;
import java.time.Instant;
import java.time.Year;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Customer aggregate: identity (PII), consent and preferences. Data minimization is built in: only
 * the birth year is kept, never a full date of birth.
 */
public final class Customer {

  private static final Pattern COUNTRY = Pattern.compile("[A-Z]{2}");
  private static final int MIN_BIRTH_YEAR = 1900;
  private static final int MIN_AGE = 18;

  private final CustomerId id;
  private final TenantId tenantId;
  private final Instant createdAt;
  private final Long version;
  private PersonalData personalData;
  private String countryCode;
  private int birthYear;
  private Preferences preferences;
  private boolean marketingConsent;
  private Instant consentUpdatedAt;
  private CustomerStatus status;
  private Instant updatedAt;

  private Customer(State s) {
    this.id = Objects.requireNonNull(s.id(), "id");
    this.tenantId = Objects.requireNonNull(s.tenantId(), "tenantId");
    this.personalData = Objects.requireNonNull(s.personalData(), "personalData");
    this.countryCode = validCountry(s.countryCode());
    this.birthYear = validBirthYear(s.birthYear(), s.createdAt());
    this.preferences = Objects.requireNonNull(s.preferences(), "preferences");
    this.marketingConsent = s.marketingConsent();
    this.consentUpdatedAt = s.consentUpdatedAt();
    this.status = Objects.requireNonNull(s.status(), "status");
    this.createdAt = Objects.requireNonNull(s.createdAt(), "createdAt");
    this.updatedAt = Objects.requireNonNull(s.updatedAt(), "updatedAt");
    this.version = s.version();
  }

  public record State(
      CustomerId id,
      TenantId tenantId,
      PersonalData personalData,
      String countryCode,
      int birthYear,
      Preferences preferences,
      boolean marketingConsent,
      Instant consentUpdatedAt,
      CustomerStatus status,
      Instant createdAt,
      Instant updatedAt,
      Long version) {}

  /** Registers a customer without marketing consent; consent is always an explicit act. */
  public static Customer register(
      TenantId tenantId,
      PersonalData personalData,
      String countryCode,
      int birthYear,
      Preferences preferences,
      Instant now) {
    return new Customer(
        new State(
            CustomerId.newId(),
            tenantId,
            personalData,
            countryCode,
            birthYear,
            preferences,
            false,
            null,
            CustomerStatus.ACTIVE,
            now,
            now,
            null));
  }

  public static Customer restore(State state) {
    return new Customer(state);
  }

  public void updateContact(PersonalData newData, Instant now) {
    requireActive();
    personalData = Objects.requireNonNull(newData, "personalData");
    updatedAt = now;
  }

  public void updatePreferences(Preferences newPreferences, Instant now) {
    requireActive();
    preferences = Objects.requireNonNull(newPreferences, "preferences");
    updatedAt = now;
  }

  public void grantMarketingConsent(Instant now) {
    requireActive();
    marketingConsent = true;
    consentUpdatedAt = now;
    updatedAt = now;
  }

  /** Withdrawal is always possible, including for closed customers. */
  public void withdrawMarketingConsent(Instant now) {
    marketingConsent = false;
    consentUpdatedAt = now;
    updatedAt = now;
  }

  public void close(Instant now) {
    requireActive();
    status = CustomerStatus.CLOSED;
    withdrawMarketingConsent(now);
  }

  /** Marketing may reach this customer on a channel only with consent and a channel opt-in. */
  public boolean isContactableBy(Channel channel) {
    if (status != CustomerStatus.ACTIVE || !marketingConsent) {
      return false;
    }
    return switch (channel) {
      case EMAIL -> preferences.emailOptIn();
      case PUSH -> preferences.pushOptIn();
      case SMS -> preferences.smsOptIn() && personalData.phoneNumber().isPresent();
    };
  }

  /** Marketing channels that need an opt-in. */
  public enum Channel {
    EMAIL,
    PUSH,
    SMS
  }

  private void requireActive() {
    if (status != CustomerStatus.ACTIVE) {
      throw new DomainRuleViolation(CustomerErrors.CLOSED, "customer " + id + " is closed");
    }
  }

  private static String validCountry(String code) {
    if (code == null || !COUNTRY.matcher(code).matches()) {
      throw new DomainRuleViolation(CustomerErrors.INVALID_PROFILE, "country must be ISO alpha-2");
    }
    return code;
  }

  private static int validBirthYear(int year, Instant now) {
    int currentYear = Year.from(now.atZone(ZoneOffset.UTC)).getValue();
    if (year < MIN_BIRTH_YEAR || year > currentYear - MIN_AGE) {
      throw new DomainRuleViolation(
          CustomerErrors.INVALID_PROFILE,
          "birth year must be " + MIN_BIRTH_YEAR + "-" + (currentYear - MIN_AGE));
    }
    return year;
  }

  public CustomerId id() {
    return id;
  }

  public TenantId tenantId() {
    return tenantId;
  }

  public PersonalData personalData() {
    return personalData;
  }

  public String countryCode() {
    return countryCode;
  }

  public int birthYear() {
    return birthYear;
  }

  public Preferences preferences() {
    return preferences;
  }

  public boolean marketingConsent() {
    return marketingConsent;
  }

  public Optional<Instant> consentUpdatedAt() {
    return Optional.ofNullable(consentUpdatedAt);
  }

  public CustomerStatus status() {
    return status;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant updatedAt() {
    return updatedAt;
  }

  public Optional<Long> version() {
    return Optional.ofNullable(version);
  }
}
