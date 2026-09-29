package com.vasmarketing.offer.application;

import static com.vasmarketing.offer.OfferFixtures.ADMIN;
import static com.vasmarketing.offer.OfferFixtures.travelRules;
import static com.vasmarketing.offer.OfferFixtures.travelTerms;
import static org.assertj.core.api.Assertions.assertThat;

import com.vasmarketing.offer.domain.model.Offer;
import com.vasmarketing.platform.testsupport.ServicePostgres;
import com.vasmarketing.platform.types.Precondition;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class OfferOutboxIntegrationTest {

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    ServicePostgres.register(registry, "offer");
  }

  @Autowired private OfferManagementService offers;
  @Autowired private JdbcTemplate jdbc;

  @Test
  void everyOfferChangeIsRecordedInOrderInTheOutbox() {
    Offer offer = offers.create(ADMIN, travelTerms("Outbox " + UUID.randomUUID()), travelRules());
    offers.activate(ADMIN, offer.id(), Precondition.none());
    offers.pause(ADMIN, offer.id(), Precondition.none());

    List<String> types =
        jdbc.queryForList(
            "select event_type from outbox_events where message_key = ? and topic = 'offer.events' order by created_at, id",
            String.class,
            offer.id().toString());

    assertThat(types).containsExactly("OfferCreated", "OfferActivated", "OfferPaused");
  }
}
