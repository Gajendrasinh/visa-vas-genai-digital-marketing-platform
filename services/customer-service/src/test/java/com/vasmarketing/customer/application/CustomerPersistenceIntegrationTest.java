package com.vasmarketing.customer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vasmarketing.customer.domain.customer.Customer;
import com.vasmarketing.customer.domain.customer.CustomerErrors;
import com.vasmarketing.customer.domain.customer.PersonalData;
import com.vasmarketing.customer.domain.customer.Preferences;
import com.vasmarketing.customer.domain.customer.SpendCategory;
import com.vasmarketing.customer.domain.merchant.Merchant;
import com.vasmarketing.platform.testsupport.ServicePostgres;
import com.vasmarketing.platform.types.Actor;
import com.vasmarketing.platform.types.Precondition;
import com.vasmarketing.platform.types.ResourceNotFound;
import com.vasmarketing.platform.types.TenantId;
import com.vasmarketing.platform.types.UserId;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CustomerPersistenceIntegrationTest {

  private static final Actor ISSUER_A =
      new Actor(new UserId("issuer-a"), new TenantId(UUID.randomUUID()));
  private static final Actor ISSUER_B =
      new Actor(new UserId("issuer-b"), new TenantId(UUID.randomUUID()));

  @DynamicPropertySource
  static void configure(DynamicPropertyRegistry registry) {
    ServicePostgres.register(registry, "customer");
    registry.add("customer.pii.keys.1", CustomerPersistenceIntegrationTest::randomKey);
    registry.add("customer.pii.hmac-key", CustomerPersistenceIntegrationTest::randomKey);
  }

  @Autowired private CustomerService customers;
  @Autowired private MerchantService merchants;

  private static String randomKey() {
    byte[] key = new byte[32];
    new SecureRandom().nextBytes(key);
    return Base64.getEncoder().encodeToString(key);
  }

  private static RegisterCustomerCommand command(String email) {
    return new RegisterCustomerCommand(
        new PersonalData("Jane Doe", email, "+14155550123"),
        "US",
        1988,
        new Preferences(
            true, true, false, Set.of(SpendCategory.TRAVEL, SpendCategory.DINING), "en"));
  }

  private static String uniqueEmail() {
    return "jane+" + UUID.randomUUID() + "@example.com";
  }

  @Test
  void roundTripsCustomerWithConsentAndPreferences() {
    Customer registered = customers.register(ISSUER_A, command(uniqueEmail()));
    customers.grantMarketingConsent(ISSUER_A, registered.id(), Precondition.none());

    Customer reloaded = customers.get(ISSUER_A, registered.id());

    assertThat(reloaded.personalData()).isEqualTo(registered.personalData());
    assertThat(reloaded.preferences()).isEqualTo(registered.preferences());
    assertThat(reloaded.marketingConsent()).isTrue();
    assertThat(reloaded.isContactableBy(Customer.Channel.EMAIL)).isTrue();
  }

  @Test
  void piiIsStoredOnlyAsCiphertext() throws SQLException {
    String email = uniqueEmail();
    Customer registered = customers.register(ISSUER_A, command(email));

    try (Connection admin = ServicePostgres.adminConnection();
        PreparedStatement query =
            admin.prepareStatement(
                "select full_name_enc, email_enc, email_hmac from customer.customers where id = ?")) {
      query.setObject(1, registered.id().value());
      try (ResultSet row = query.executeQuery()) {
        assertThat(row.next()).isTrue();
        assertThat(new String(row.getBytes("full_name_enc"), StandardCharsets.ISO_8859_1))
            .doesNotContain("Jane");
        assertThat(new String(row.getBytes("email_enc"), StandardCharsets.ISO_8859_1))
            .doesNotContain("example.com");
        assertThat(row.getString("email_hmac")).doesNotContain("@").hasSize(64);
      }
    }
  }

  @Test
  void rowLevelSecurityHidesRowsWithoutMatchingTenant() throws SQLException {
    customers.register(ISSUER_A, command(uniqueEmail()));

    try (Connection service = ServicePostgres.serviceConnection("customer")) {
      service.setAutoCommit(false);
      assertThat(count(service)).as("no tenant bound: default deny").isZero();
      bindTenant(service, ISSUER_B.tenantId());
      assertThat(count(service)).as("other tenant").isZero();
      bindTenant(service, ISSUER_A.tenantId());
      assertThat(count(service)).as("own tenant").isPositive();
      service.rollback();
    }
  }

  @Test
  void customersAreTenantScopedAndEmailsUniquePerTenant() {
    String email = uniqueEmail();
    Customer registered = customers.register(ISSUER_A, command(email));

    assertThatThrownBy(() -> customers.get(ISSUER_B, registered.id()))
        .isInstanceOf(ResourceNotFound.class);
    assertThatThrownBy(() -> customers.register(ISSUER_A, command(email.toUpperCase())))
        .extracting("code")
        .isEqualTo(CustomerErrors.DUPLICATE_EMAIL);
    assertThat(customers.register(ISSUER_B, command(email)).tenantId())
        .isEqualTo(ISSUER_B.tenantId());
  }

  @Test
  void contactPreferencesAndClosureArePersisted() {
    Customer registered = customers.register(ISSUER_A, command(uniqueEmail()));
    Precondition none = Precondition.none();
    customers.updatePreferences(ISSUER_A, registered.id(), Preferences.defaults("en-GB"), none);
    customers.updateContact(
        ISSUER_A, registered.id(), new PersonalData("Jane Smith", uniqueEmail(), null), none);
    customers.grantMarketingConsent(ISSUER_A, registered.id(), none);
    customers.withdrawMarketingConsent(ISSUER_A, registered.id(), none);
    Customer closed = customers.close(ISSUER_A, registered.id(), none);

    Customer reloaded = customers.get(ISSUER_A, closed.id());
    assertThat(reloaded.personalData().fullName()).isEqualTo("Jane Smith");
    assertThat(reloaded.personalData().phoneNumber()).isEmpty();
    assertThat(reloaded.preferences().language()).isEqualTo("en-GB");
    assertThat(reloaded.status())
        .isEqualTo(com.vasmarketing.customer.domain.customer.CustomerStatus.CLOSED);
  }

  @Test
  void merchantsRoundTripWithDerivedCategory() {
    Merchant merchant = merchants.register("SkyWays Air", "4511", "US", "Denver");
    merchants.update(merchant.id(), "SkyWays Airlines", "4511", "Boulder", Precondition.none());
    merchants.deactivate(merchant.id(), Precondition.none());

    Merchant reloaded = merchants.get(merchant.id());
    assertThat(reloaded.name()).isEqualTo("SkyWays Airlines");
    assertThat(reloaded.category()).isEqualTo(SpendCategory.TRAVEL);
    assertThat(reloaded.active()).isFalse();
  }

  private static long count(Connection connection) throws SQLException {
    try (Statement statement = connection.createStatement();
        ResultSet rs = statement.executeQuery("select count(*) from customers")) {
      rs.next();
      return rs.getLong(1);
    }
  }

  private static void bindTenant(Connection connection, TenantId tenant) throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement("select set_config('app.tenant_id', ?, true)")) {
      statement.setString(1, tenant.toString());
      statement.execute();
    }
  }
}
