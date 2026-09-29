package com.vasmarketing.platform.kafka;

import java.util.Objects;
import java.util.UUID;
import org.apache.avro.specific.SpecificRecord;

/**
 * An event to publish after the surrounding transaction commits.
 *
 * @param key partition key (e.g. campaignId, customerId): events of one key keep their order
 * @param eventId the event's unique id (its outbox row id and consumers' dedup key)
 */
public record OutboxMessage(
    String topic, String key, UUID eventId, String eventType, SpecificRecord payload) {

  public OutboxMessage {
    Objects.requireNonNull(topic, "topic");
    Objects.requireNonNull(key, "key");
    Objects.requireNonNull(eventId, "eventId");
    Objects.requireNonNull(eventType, "eventType");
    Objects.requireNonNull(payload, "payload");
  }
}
