package com.vasmarketing.segment.application.dsl;

import com.vasmarketing.platform.types.DomainRuleViolation;
import com.vasmarketing.segment.domain.criteria.Criterion;
import com.vasmarketing.segment.domain.criteria.Feature;
import com.vasmarketing.segment.domain.criteria.Operator;
import com.vasmarketing.segment.domain.criteria.SegmentDefinition;
import com.vasmarketing.segment.domain.criteria.SegmentErrorCodes;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The segment DSL's JSON form, shared by the API and storage:
 *
 * <pre>{"all":[{"feature":"travel_txn_count_90d","op":"gte","value":3}, {"any":[...]}]}</pre>
 *
 * Parsing is strict: unknown keys, features or operators are rejected rather than ignored.
 */
public final class SegmentDefinitionJson {

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final Set<String> CONDITION_KEYS = Set.of("feature", "op", "value");

  private SegmentDefinitionJson() {}

  public static SegmentDefinition parse(String json) {
    try {
      return new SegmentDefinition(criterion(JSON.readTree(json), 1));
    } catch (JacksonException e) {
      throw invalid("definition is not valid JSON");
    }
  }

  public static String write(SegmentDefinition definition) {
    return JSON.writeValueAsString(node(definition.root()));
  }

  private static Criterion criterion(JsonNode node, int depth) {
    if (depth > SegmentDefinition.MAX_DEPTH) {
      throw invalid(
          "nesting deeper than " + SegmentDefinition.MAX_DEPTH + " levels is not allowed");
    }
    if (!node.isObject()) {
      throw invalid("each criterion must be a JSON object");
    }
    Set<String> keys = Set.copyOf(node.propertyNames());
    if (keys.equals(Set.of("all"))) {
      return new Criterion.All(children(node.get("all"), depth));
    }
    if (keys.equals(Set.of("any"))) {
      return new Criterion.Any(children(node.get("any"), depth));
    }
    if (keys.equals(CONDITION_KEYS)) {
      return condition(node);
    }
    throw invalid("unexpected keys " + keys + "; use all, any or feature/op/value");
  }

  private static List<Criterion> children(JsonNode array, int depth) {
    if (!array.isArray()) {
      throw invalid("all/any must contain an array");
    }
    List<Criterion> criteria = new ArrayList<>();
    array.forEach(child -> criteria.add(criterion(child, depth + 1)));
    return criteria;
  }

  private static Criterion condition(JsonNode node) {
    String featureKey = node.get("feature").asString();
    Feature feature =
        Feature.fromKey(featureKey).orElseThrow(() -> invalid("unknown feature " + featureKey));
    String opKey = node.get("op").asString();
    Operator op = Operator.fromKey(opKey).orElseThrow(() -> invalid("unknown operator " + opKey));
    JsonNode value = node.get("value");
    return switch (feature.type()) {
      case NUMBER -> {
        if (!value.isNumber()) {
          throw invalid(featureKey + " needs a numeric value");
        }
        yield new Criterion.NumberCondition(feature, op, value.decimalValue());
      }
      case BOOLEAN -> {
        if (!value.isBoolean()) {
          throw invalid(featureKey + " needs a boolean value");
        }
        yield new Criterion.BooleanCondition(feature, op, value.booleanValue());
      }
    };
  }

  private static JsonNode node(Criterion criterion) {
    ObjectNode node = JSON.createObjectNode();
    switch (criterion) {
      case Criterion.All all -> addChildren(node.putArray("all"), all.criteria());
      case Criterion.Any any -> addChildren(node.putArray("any"), any.criteria());
      case Criterion.NumberCondition c -> {
        node.put("feature", c.feature().key());
        node.put("op", c.operator().key());
        node.put("value", c.value());
      }
      case Criterion.BooleanCondition c -> {
        node.put("feature", c.feature().key());
        node.put("op", c.operator().key());
        node.put("value", c.value());
      }
    }
    return node;
  }

  private static void addChildren(ArrayNode array, List<Criterion> criteria) {
    criteria.forEach(c -> array.add(node(c)));
  }

  private static DomainRuleViolation invalid(String message) {
    return new DomainRuleViolation(SegmentErrorCodes.INVALID_DEFINITION, message);
  }
}
