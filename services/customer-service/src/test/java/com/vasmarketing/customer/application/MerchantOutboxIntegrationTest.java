package com.vasmarketing.customer.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.vasmarketing.customer.domain.merchant.Merchant;
import com.vasmarketing.platform.testsupport.ServicePostgres;
import com.vasmarketing.platform.types.Precondition;
import java.security.SecureRandom;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class MerchantOutboxIntegrationTest {

  @DynamicPropertySource
  static void configure(DynamicPropertyRegistry registry) {
    ServicePostgres.register(registry, "customer");
    registry.add("customer.pii.keys.1", MerchantOutboxIntegrationTest::randomKey);
    registry.add("customer.pii.hmac-key", MerchantOutboxIntegrationTest::randomKey);
  }

  @Autowired private MerchantService merchants;
  @Autowired private JdbcTemplate jdbc;

  private static String randomKey() {
    byte[] key = new byte[32];
    new SecureRandom().nextBytes(key);
    return Base64.getEncoder().encodeToString(key);
  }

  @Test
  void everyMerchantChangePublishesItsLatestSnapshot() {
    Merchant merchant = merchants.register("Harbor Hotel", "7011", "US", "Boston");
    merchants.deactivate(merchant.id(), Precondition.none());

    Long count =
        jdbc.queryForObject(
            "select count(*) from outbox_events where topic = 'merchant.reference' and message_key = ?",
            Long.class,
            merchant.id().toString());
    assertThat(count).isEqualTo(2);
  }
}
