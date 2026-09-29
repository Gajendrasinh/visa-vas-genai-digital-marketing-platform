package com.vasmarketing.segment.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vasmarketing.platform.types.Actor;
import com.vasmarketing.platform.types.TenantId;
import com.vasmarketing.platform.types.UserId;
import com.vasmarketing.segment.application.dsl.SegmentDefinitionJson;
import com.vasmarketing.segment.domain.criteria.Feature;
import com.vasmarketing.segment.domain.criteria.FeatureVector;
import com.vasmarketing.segment.domain.criteria.SegmentDefinition;
import com.vasmarketing.segment.domain.criteria.SegmentErrorCodes;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SegmentTest {

  private static final Instant NOW = Instant.parse("2026-10-01T09:00:00Z");
  private static final Actor ANALYST =
      new Actor(new UserId("data"), new TenantId(UUID.randomUUID()));
  private static final SegmentDefinition ACTIVE_BUYERS =
      SegmentDefinitionJson.parse("{\"feature\":\"txn_count_30d\",\"op\":\"gte\",\"value\":10}");

  private static Segment draft() {
    return Segment.create(ANALYST, "Active buyers", null, ACTIVE_BUYERS, SegmentOrigin.MANUAL, NOW);
  }

  @Test
  void onlyActiveSegmentsIncludeCustomers() {
    Segment segment = draft();
    FeatureVector buyer = FeatureVector.builder().number(Feature.TXN_COUNT_30D, 12).build();

    assertThat(segment.includes(buyer)).isFalse();
    segment.activate(NOW);
    assertThat(segment.includes(buyer)).isTrue();
    segment.retire(NOW);
    assertThat(segment.includes(buyer)).isFalse();
  }

  @Test
  void definitionIsFrozenOnceActive() {
    Segment segment = draft();
    segment.redefine(ACTIVE_BUYERS, NOW);
    assertThat(segment.definitionVersion()).isEqualTo(2);

    segment.activate(NOW);
    assertThatThrownBy(() -> segment.redefine(ACTIVE_BUYERS, NOW))
        .extracting("code")
        .isEqualTo(SegmentErrorCodes.NOT_EDITABLE);
  }

  @Test
  void invalidTransitionsAreRejected() {
    Segment segment = draft();
    assertThatThrownBy(() -> segment.retire(NOW))
        .extracting("code")
        .isEqualTo(SegmentErrorCodes.INVALID_TRANSITION);
    segment.activate(NOW);
    assertThatThrownBy(() -> segment.activate(NOW))
        .extracting("code")
        .isEqualTo(SegmentErrorCodes.INVALID_TRANSITION);
  }

  @Test
  void validatesNameAndDescription() {
    assertThatThrownBy(
            () -> Segment.create(ANALYST, " ", null, ACTIVE_BUYERS, SegmentOrigin.MANUAL, NOW))
        .extracting("code")
        .isEqualTo(SegmentErrorCodes.INVALID_DETAILS);
    Segment segment = draft();
    assertThatThrownBy(() -> segment.describe("ok", "x".repeat(501), NOW))
        .extracting("code")
        .isEqualTo(SegmentErrorCodes.INVALID_DETAILS);
    segment.describe("Renamed", "Buyers with 10+ transactions", NOW);
    assertThat(segment.description()).contains("Buyers with 10+ transactions");
  }
}
