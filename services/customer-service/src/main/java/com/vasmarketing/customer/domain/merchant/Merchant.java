package com.vasmarketing.customer.domain.merchant;

import com.vasmarketing.customer.domain.customer.SpendCategory;
import com.vasmarketing.platform.types.DomainRuleViolation;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/** Network-wide merchant reference data (not tenant-owned). */
public final class Merchant {

  private static final Pattern COUNTRY = Pattern.compile("[A-Z]{2}");
  private static final int MAX_NAME = 150;
  private static final int MAX_CITY = 100;

  private final MerchantId id;
  private final Instant createdAt;
  private final Long version;
  private String name;
  private MerchantCategoryCode mcc;
  private String countryCode;
  private String city;
  private boolean active;
  private Instant updatedAt;

  private Merchant(State s) {
    this.id = Objects.requireNonNull(s.id(), "id");
    this.name = validName(s.name());
    this.mcc = Objects.requireNonNull(s.mcc(), "mcc");
    this.countryCode = validCountry(s.countryCode());
    this.city = validCity(s.city());
    this.active = s.active();
    this.createdAt = Objects.requireNonNull(s.createdAt(), "createdAt");
    this.updatedAt = Objects.requireNonNull(s.updatedAt(), "updatedAt");
    this.version = s.version();
  }

  public record State(
      MerchantId id,
      String name,
      MerchantCategoryCode mcc,
      String countryCode,
      String city,
      boolean active,
      Instant createdAt,
      Instant updatedAt,
      Long version) {}

  public static Merchant register(
      String name, MerchantCategoryCode mcc, String countryCode, String city, Instant now) {
    return new Merchant(
        new State(MerchantId.newId(), name, mcc, countryCode, city, true, now, now, null));
  }

  public static Merchant restore(State state) {
    return new Merchant(state);
  }

  public void update(String newName, MerchantCategoryCode newMcc, String newCity, Instant now) {
    name = validName(newName);
    mcc = Objects.requireNonNull(newMcc, "mcc");
    city = validCity(newCity);
    updatedAt = now;
  }

  public void deactivate(Instant now) {
    if (!active) {
      throw new DomainRuleViolation(MerchantErrors.INVALID_TRANSITION, "merchant already inactive");
    }
    active = false;
    updatedAt = now;
  }

  public SpendCategory category() {
    return mcc.category();
  }

  private static String validName(String value) {
    if (value == null || value.isBlank() || value.length() > MAX_NAME) {
      throw new DomainRuleViolation(
          MerchantErrors.INVALID_MERCHANT, "name must be 1-" + MAX_NAME + " characters");
    }
    return value;
  }

  private static String validCountry(String value) {
    if (value == null || !COUNTRY.matcher(value).matches()) {
      throw new DomainRuleViolation(MerchantErrors.INVALID_MERCHANT, "country must be ISO alpha-2");
    }
    return value;
  }

  private static String validCity(String value) {
    if (value != null && (value.isBlank() || value.length() > MAX_CITY)) {
      throw new DomainRuleViolation(
          MerchantErrors.INVALID_MERCHANT, "city must be 1-" + MAX_CITY + " characters");
    }
    return value;
  }

  public MerchantId id() {
    return id;
  }

  public String name() {
    return name;
  }

  public MerchantCategoryCode mcc() {
    return mcc;
  }

  public String countryCode() {
    return countryCode;
  }

  public Optional<String> city() {
    return Optional.ofNullable(city);
  }

  public boolean active() {
    return active;
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
