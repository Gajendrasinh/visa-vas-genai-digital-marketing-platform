package com.vasmarketing.segment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vasmarketing.platform.testsupport.ServicePostgres;
import com.vasmarketing.platform.types.Actor;
import com.vasmarketing.platform.types.Precondition;
import com.vasmarketing.platform.types.ResourceNotFound;
import com.vasmarketing.platform.types.TenantId;
import com.vasmarketing.platform.types.UserId;
import com.vasmarketing.segment.application.dsl.SegmentDefinitionJson;
import com.vasmarketing.segment.domain.criteria.SegmentDefinition;
import com.vasmarketing.segment.domain.criteria.SegmentErrorCodes;
import com.vasmarketing.segment.domain.model.Segment;
import com.vasmarketing.segment.domain.model.SegmentOrigin;
import com.vasmarketing.segment.domain.model.SegmentStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SegmentPersistenceIntegrationTest {

  private static final Actor ANALYST =
      new Actor(
          new UserId("data"),
          new TenantId(UUID.fromString("0191f000-0000-7000-8000-000000000001")));
  private static final SegmentDefinition TRAVELERS =
      SegmentDefinitionJson.parse(
          "{\"all\":[{\"feature\":\"travel_txn_count_90d\",\"op\":\"gte\",\"value\":3},"
              + "{\"feature\":\"marketing_consent\",\"op\":\"eq\",\"value\":true}]}");

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    ServicePostgres.register(registry, "segment");
  }

  @Autowired private SegmentService segments;

  @Test
  void persistsDefinitionAndLifecycle() {
    Segment created =
        segments.create(
            ANALYST,
            "Travelers " + UUID.randomUUID(),
            "Frequent travelers",
            TRAVELERS,
            SegmentOrigin.AI_PROPOSED);
    segments.activate(ANALYST, created.id(), Precondition.none());

    Segment reloaded = segments.get(ANALYST, created.id());

    assertThat(reloaded.definition()).isEqualTo(TRAVELERS);
    assertThat(reloaded.status()).isEqualTo(SegmentStatus.ACTIVE);
    assertThat(reloaded.origin()).isEqualTo(SegmentOrigin.AI_PROPOSED);
    assertThat(reloaded.description()).contains("Frequent travelers");
  }

  @Test
  void redefinitionAndRenameArePersisted() {
    Segment created =
        segments.create(
            ANALYST, "Draft " + UUID.randomUUID(), null, TRAVELERS, SegmentOrigin.MANUAL);
    SegmentDefinition stricter =
        SegmentDefinitionJson.parse(
            "{\"feature\":\"travel_txn_count_90d\",\"op\":\"gte\",\"value\":6}");

    String newName = "Renamed " + UUID.randomUUID();
    segments.update(ANALYST, created.id(), newName, "stricter", stricter, Precondition.none());

    Segment reloaded = segments.get(ANALYST, created.id());
    assertThat(reloaded.definition()).isEqualTo(stricter);
    assertThat(reloaded.definitionVersion()).isEqualTo(2);
    assertThat(reloaded.name()).isEqualTo(newName);
  }

  @Test
  void namesAreUniquePerTenantAndSegmentsTenantScoped() {
    String name = "Unique " + UUID.randomUUID();
    Segment created = segments.create(ANALYST, name, null, TRAVELERS, SegmentOrigin.MANUAL);

    assertThatThrownBy(() -> segments.create(ANALYST, name, null, TRAVELERS, SegmentOrigin.MANUAL))
        .extracting("code")
        .isEqualTo(SegmentErrorCodes.DUPLICATE_NAME);
    Actor other = new Actor(new UserId("data"), new TenantId(UUID.randomUUID()));
    assertThatThrownBy(() -> segments.get(other, created.id()))
        .isInstanceOf(ResourceNotFound.class);
    assertThatThrownBy(() -> segments.retire(other, created.id(), Precondition.none()))
        .isInstanceOf(ResourceNotFound.class);
  }
}
