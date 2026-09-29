package com.vasmarketing.customer.domain.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vasmarketing.platform.types.TenantId;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CustomerTest {

  private static final Instant NOW = Instant.parse("2026-10-01T09:00:00Z");
  private static final TenantId TENANT = new TenantId(UUID.randomUUID());
  private static final PersonalData JANE =
      new PersonalData("Jane Doe", "Jane.Doe@Example.com", "+14155550123");

  private static Customer jane() {
    Preferences prefs = new Preferences(true, false, true, Set.of(SpendCategory.TRAVEL), "en");
    return Customer.register(TENANT, JANE, "US", 1988, prefs, NOW);
  }

  @Test
  void newCustomersHaveNoMarketingConsent() {
    Customer customer = jane();

    assertThat(customer.marketingConsent()).isFalse();
    assertThat(customer.isContactableBy(Customer.Channel.EMAIL)).isFalse();
    assertThat(customer.status()).isEqualTo(CustomerStatus.ACTIVE);
  }

  @Test
  void contactabilityNeedsConsentAndChannelOptIn() {
    Customer customer = jane();
    customer.grantMarketingConsent(NOW);

    assertThat(customer.isContactableBy(Customer.Channel.EMAIL)).isTrue();
    assertThat(customer.isContactableBy(Customer.Channel.PUSH)).isFalse();
    assertThat(customer.isContactableBy(Customer.Channel.SMS)).isTrue();
    assertThat(customer.consentUpdatedAt()).contains(NOW);

    customer.updateContact(new PersonalData("Jane Doe", "jane@example.com", null), NOW);
    assertThat(customer.isContactableBy(Customer.Channel.SMS))
        .as("SMS needs a phone number")
        .isFalse();
  }

  @Test
  void closingWithdrawsConsentAndFreezesTheRecord() {
    Customer customer = jane();
    customer.grantMarketingConsent(NOW);
    customer.close(NOW);

    assertThat(customer.marketingConsent()).isFalse();
    assertThat(customer.isContactableBy(Customer.Channel.EMAIL)).isFalse();
    assertThatThrownBy(() -> customer.grantMarketingConsent(NOW))
        .extracting("code")
        .isEqualTo(CustomerErrors.CLOSED);
    assertThatThrownBy(() -> customer.updatePreferences(Preferences.defaults("en"), NOW))
        .extracting("code")
        .isEqualTo(CustomerErrors.CLOSED);
    customer.withdrawMarketingConsent(NOW);
  }

  @Test
  void keepsOnlyBirthYearAndValidatesIt() {
    assertThatThrownBy(
            () -> Customer.register(TENANT, JANE, "US", 2015, Preferences.defaults("en"), NOW))
        .extracting("code")
        .isEqualTo(CustomerErrors.INVALID_PROFILE);
    assertThatThrownBy(
            () -> Customer.register(TENANT, JANE, "USA", 1980, Preferences.defaults("en"), NOW))
        .extracting("code")
        .isEqualTo(CustomerErrors.INVALID_PROFILE);
  }

  @Test
  void emailIsNormalizedAndPiiIsMaskedInToString() {
    assertThat(JANE.email()).isEqualTo("jane.doe@example.com");
    assertThat(JANE.toString()).doesNotContain("Jane Doe", "jane.doe@", "4155550123");
    assertThat(JANE.masked())
        .isEqualTo(
            new PersonalData.MaskedPersonalData("J*** D***", "j***@example.com", "+***0123"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"not-an-email", "a@b", "@example.com"})
  void rejectsInvalidEmail(String email) {
    assertThatThrownBy(() -> new PersonalData("Jane", email, null))
        .extracting("code")
        .isEqualTo(CustomerErrors.INVALID_PERSONAL_DATA);
  }

  @ParameterizedTest
  @ValueSource(strings = {"4155550123", "+0123456789", "+1 415 555"})
  void rejectsNonE164Phone(String phone) {
    assertThatThrownBy(() -> new PersonalData("Jane", "jane@example.com", phone))
        .extracting("code")
        .isEqualTo(CustomerErrors.INVALID_PERSONAL_DATA);
  }

  @Test
  void preferencesValidateLanguage() {
    assertThatThrownBy(() -> Preferences.defaults("english"))
        .extracting("code")
        .isEqualTo(CustomerErrors.INVALID_PROFILE);
    assertThat(Preferences.defaults("en-GB").preferredCategories()).isEmpty();
  }
}
