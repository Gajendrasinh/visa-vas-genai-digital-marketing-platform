package com.vasmarketing.segment.domain.criteria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vasmarketing.platform.types.DomainRuleViolation;
import com.vasmarketing.segment.application.dsl.SegmentDefinitionJson;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class SegmentDefinitionTest {

  private static final String FREQUENT_TRAVELERS =
      """
      {"all":[
        {"feature":"travel_txn_count_90d","op":"gte","value":3},
        {"feature":"txn_amount_avg_30d","op":"gte","value":80},
        {"feature":"marketing_consent","op":"eq","value":true},
        {"any":[
          {"feature":"engagement_score","op":"gt","value":0.5},
          {"feature":"days_since_last_activity","op":"lte","value":7}
        ]}
      ]}
      """;

  private static FeatureVector traveler(
      long travelTxns, String avgAmount, boolean consent, String engagement, long idleDays) {
    return FeatureVector.builder()
        .number(Feature.TRAVEL_TXN_COUNT_90D, travelTxns)
        .number(Feature.TXN_AMOUNT_AVG_30D, new BigDecimal(avgAmount))
        .bool(Feature.MARKETING_CONSENT, consent)
        .number(Feature.ENGAGEMENT_SCORE, new BigDecimal(engagement))
        .number(Feature.DAYS_SINCE_LAST_ACTIVITY, idleDays)
        .build();
  }

  @Test
  void matchesFrequentTraveler() {
    SegmentDefinition definition = SegmentDefinitionJson.parse(FREQUENT_TRAVELERS);

    assertThat(definition.matches(traveler(4, "120.50", true, "0.7", 30))).isTrue();
    assertThat(definition.matches(traveler(4, "120.50", true, "0.1", 3))).isTrue();
  }

  @ParameterizedTest
  @CsvSource({
    "2, 120.50, true, 0.7, 3", // too few travel transactions
    "4, 79.99, true, 0.7, 3", // average spend too low
    "4, 120.50, false, 0.7, 3", // no consent
    "4, 120.50, true, 0.4, 8" // neither engaged nor recently active
  })
  void rejectsNonMatchingCustomers(
      long travel, String avg, boolean consent, String engagement, long idle) {
    SegmentDefinition definition = SegmentDefinitionJson.parse(FREQUENT_TRAVELERS);

    assertThat(definition.matches(traveler(travel, avg, consent, engagement, idle))).isFalse();
  }

  @Test
  void missingFeaturesNeverMatch() {
    SegmentDefinition definition = SegmentDefinitionJson.parse(FREQUENT_TRAVELERS);

    assertThat(definition.matches(FeatureVector.builder().build())).isFalse();
  }

  @Test
  void jsonRoundTripPreservesTheDefinition() {
    SegmentDefinition definition = SegmentDefinitionJson.parse(FREQUENT_TRAVELERS);

    assertThat(SegmentDefinitionJson.parse(SegmentDefinitionJson.write(definition)))
        .isEqualTo(definition);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{\"feature\":\"credit_score\",\"op\":\"gt\",\"value\":1}",
        "{\"feature\":\"txn_count_30d\",\"op\":\"like\",\"value\":1}",
        "{\"feature\":\"txn_count_30d\",\"op\":\"gt\",\"value\":\"1; drop table\"}",
        "{\"feature\":\"marketing_consent\",\"op\":\"gt\",\"value\":true}",
        "{\"feature\":\"marketing_consent\",\"op\":\"eq\",\"value\":1}",
        "{\"all\":[]}",
        "{\"all\":{}}",
        "{\"feature\":\"txn_count_30d\",\"op\":\"gt\",\"value\":1,\"sql\":\"x\"}",
        "[]",
        "not json"
      })
  void rejectsInvalidDefinitions(String json) {
    assertThatThrownBy(() -> SegmentDefinitionJson.parse(json))
        .isInstanceOf(DomainRuleViolation.class)
        .extracting("code")
        .isEqualTo(SegmentErrorCodes.INVALID_DEFINITION);
  }

  @Test
  void enforcesSizeAndDepthLimits() {
    Criterion condition =
        new Criterion.NumberCondition(Feature.TXN_COUNT_30D, Operator.GT, BigDecimal.ONE);
    assertThatThrownBy(
            () -> new SegmentDefinition(new Criterion.All(Collections.nCopies(21, condition))))
        .hasMessageContaining("20 conditions");

    Criterion deep =
        new Criterion.All(
            List.of(
                new Criterion.Any(
                    List.of(new Criterion.All(List.of(new Criterion.Any(List.of(condition))))))));
    assertThatThrownBy(() -> new SegmentDefinition(deep)).hasMessageContaining("nesting");
    String deepJson = "{\"all\":[{\"any\":[{\"all\":[{\"any\":[{\"all\":[]}]}]}]}]}";
    assertThatThrownBy(() -> SegmentDefinitionJson.parse(deepJson)).hasMessageContaining("nesting");
  }

  @ParameterizedTest
  @CsvSource({"eq,5,true", "neq,5,false", "gt,4,true", "gte,5,true", "lt,6,true", "lte,4,false"})
  void operatorsCompareNumbers(String op, int threshold, boolean expected) {
    Criterion condition =
        new Criterion.NumberCondition(
            Feature.TXN_COUNT_30D,
            Operator.fromKey(op).orElseThrow(),
            BigDecimal.valueOf(threshold));

    assertThat(condition.matches(FeatureVector.builder().number(Feature.TXN_COUNT_30D, 5).build()))
        .isEqualTo(expected);
  }

  @Test
  void vectorRejectsWrongValueTypes() {
    assertThatThrownBy(() -> FeatureVector.builder().bool(Feature.TXN_COUNT_30D, true))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> FeatureVector.builder().number(Feature.MARKETING_CONSENT, 1))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
