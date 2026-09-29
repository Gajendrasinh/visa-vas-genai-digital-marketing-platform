package com.vasmarketing.offer.infrastructure.persistence;

import com.vasmarketing.offer.domain.eligibility.EligibilityRule;
import com.vasmarketing.offer.domain.eligibility.EligibilityRule.CategoryAffinity;
import com.vasmarketing.offer.domain.eligibility.EligibilityRule.ConsentRequired;
import com.vasmarketing.offer.domain.eligibility.EligibilityRule.CountryIn;
import com.vasmarketing.offer.domain.eligibility.EligibilityRule.MinTransactions30d;
import com.vasmarketing.offer.domain.eligibility.EligibilityRule.SegmentMembership;
import com.vasmarketing.offer.domain.model.OfferCategory;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Explicit (de)serialization of rules to {@code rule_type} + {@code params}. No polymorphic JSON
 * type handling is used, so stored data can never select an arbitrary class to instantiate.
 */
final class RuleCodec {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private RuleCodec() {}

  static RuleEmbeddable encode(EligibilityRule rule) {
    ObjectNode params = JSON.createObjectNode();
    String type =
        switch (rule) {
          case SegmentMembership r -> {
            ArrayNode ids = params.putArray("segmentIds");
            r.segmentIds().stream().map(UUID::toString).sorted().forEach(ids::add);
            yield "SEGMENT_MEMBERSHIP";
          }
          case CountryIn r -> {
            ArrayNode countries = params.putArray("countryCodes");
            r.countryCodes().stream().sorted().forEach(countries::add);
            yield "COUNTRY_IN";
          }
          case MinTransactions30d r -> {
            params.put("minimum", r.minimum());
            yield "MIN_TRANSACTIONS_30D";
          }
          case CategoryAffinity r -> {
            params.put("category", r.category().name());
            params.put("minTransactions90d", r.minTransactions90d());
            yield "CATEGORY_AFFINITY";
          }
          case ConsentRequired r -> "CONSENT_REQUIRED";
        };
    return new RuleEmbeddable(type, JSON.writeValueAsString(params));
  }

  static EligibilityRule decode(RuleEmbeddable stored) {
    JsonNode params = JSON.readTree(stored.params);
    return switch (stored.ruleType) {
      case "SEGMENT_MEMBERSHIP" ->
          new SegmentMembership(strings(params.path("segmentIds"), UUID::fromString));
      case "COUNTRY_IN" -> new CountryIn(strings(params.path("countryCodes"), Function.identity()));
      case "MIN_TRANSACTIONS_30D" -> new MinTransactions30d(params.path("minimum").asInt());
      case "CATEGORY_AFFINITY" ->
          new CategoryAffinity(
              OfferCategory.valueOf(params.path("category").asString()),
              params.path("minTransactions90d").asInt());
      case "CONSENT_REQUIRED" -> new ConsentRequired();
      default -> throw new IllegalStateException("unknown stored rule type " + stored.ruleType);
    };
  }

  private static <T> Set<T> strings(JsonNode array, Function<String, T> parse) {
    Set<T> values = new LinkedHashSet<>();
    array.forEach(node -> values.add(parse.apply(node.asString())));
    return values;
  }
}
