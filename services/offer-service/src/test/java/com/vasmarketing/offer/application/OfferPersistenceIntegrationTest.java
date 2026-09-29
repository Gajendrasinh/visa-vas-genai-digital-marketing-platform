package com.vasmarketing.offer.application;

import static com.vasmarketing.offer.OfferFixtures.ADMIN;
import static com.vasmarketing.offer.OfferFixtures.travelRules;
import static com.vasmarketing.offer.OfferFixtures.travelTerms;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vasmarketing.offer.domain.eligibility.EligibilityRule;
import com.vasmarketing.offer.domain.model.Offer;
import com.vasmarketing.offer.domain.model.OfferErrors;
import com.vasmarketing.offer.domain.model.OfferStatus;
import com.vasmarketing.platform.testsupport.ServicePostgres;
import com.vasmarketing.platform.types.Actor;
import com.vasmarketing.platform.types.ResourceNotFound;
import com.vasmarketing.platform.types.TenantId;
import com.vasmarketing.platform.types.UserId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class OfferPersistenceIntegrationTest {

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    ServicePostgres.register(registry, "offer");
  }

  @Autowired private OfferManagementService offers;

  @Test
  void persistsTermsRulesAndStatus() {
    Offer created =
        offers.create(ADMIN, travelTerms("Persisted " + UUID.randomUUID()), travelRules());
    offers.activate(ADMIN, created.id());

    Offer reloaded = offers.get(ADMIN, created.id());

    assertThat(reloaded.status()).isEqualTo(OfferStatus.ACTIVE);
    assertThat(reloaded.terms()).isEqualTo(created.terms());
    assertThat(reloaded.rules()).containsExactlyElementsOf(travelRules());
    assertThat(reloaded.version()).isPresent();
  }

  @Test
  void replacingRulesBumpsRuleSetVersionAndKeepsOrder() {
    Offer created = offers.create(ADMIN, travelTerms("Rules " + UUID.randomUUID()), travelRules());
    List<EligibilityRule> replacement =
        List.of(new EligibilityRule.ConsentRequired(), new EligibilityRule.MinTransactions30d(10));

    Offer updated = offers.replaceRules(ADMIN, created.id(), replacement);

    assertThat(updated.ruleSetVersion()).isEqualTo(2);
    assertThat(offers.get(ADMIN, created.id()).rules()).containsExactlyElementsOf(replacement);
  }

  @Test
  void titlesAreUniquePerTenantAndOffersTenantScoped() {
    String title = "Unique " + UUID.randomUUID();
    Offer created = offers.create(ADMIN, travelTerms(title), travelRules());

    assertThatThrownBy(() -> offers.create(ADMIN, travelTerms(title), travelRules()))
        .extracting("code")
        .isEqualTo(OfferErrors.DUPLICATE_TITLE);
    Actor otherTenant = new Actor(new UserId("admin"), new TenantId(UUID.randomUUID()));
    assertThatThrownBy(() -> offers.get(otherTenant, created.id()))
        .isInstanceOf(ResourceNotFound.class);
  }

  @Test
  void pausedOfferTermsCanBeUpdated() {
    Offer created = offers.create(ADMIN, travelTerms("Pause " + UUID.randomUUID()), travelRules());
    offers.activate(ADMIN, created.id());
    offers.pause(ADMIN, created.id());

    String renamed = "Renamed " + UUID.randomUUID();
    Offer updated = offers.updateTerms(ADMIN, created.id(), travelTerms(renamed));

    assertThat(updated.terms().title()).isEqualTo(renamed);
    assertThat(updated.status()).isEqualTo(OfferStatus.PAUSED);
  }
}
