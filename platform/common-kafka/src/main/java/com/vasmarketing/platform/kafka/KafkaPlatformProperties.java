package com.vasmarketing.platform.kafka;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param schemaRegistryUrl Confluent-compatible Schema Registry
 * @param autoRegisterSchemas true locally; false in shared environments where CI registers schemas
 */
@ConfigurationProperties("platform.kafka")
public record KafkaPlatformProperties(
    @DefaultValue("http://localhost:8085") String schemaRegistryUrl,
    @DefaultValue("true") boolean autoRegisterSchemas,
    @DefaultValue Retry retry,
    @DefaultValue Outbox outbox) {

  /** Blocking retry before dead-lettering (order-preserving consumers, docs/kafka.md §4). */
  public record Retry(
      @DefaultValue("1s") Duration initialInterval,
      @DefaultValue("2.0") double multiplier,
      @DefaultValue("16s") Duration maxInterval,
      @DefaultValue("5") int maxAttempts) {}

  public record Outbox(
      @DefaultValue("true") boolean enabled,
      @DefaultValue("200ms") Duration pollInterval,
      @DefaultValue("200") int batchSize,
      @DefaultValue("10s") Duration sendTimeout,
      @DefaultValue("3d") Duration retention) {}
}
