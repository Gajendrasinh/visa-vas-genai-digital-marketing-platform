package com.vasmarketing.customer.infrastructure.messaging;

import com.vasmarketing.customer.domain.merchant.Merchant;
import com.vasmarketing.customer.domain.merchant.MerchantReferencePublisher;
import com.vasmarketing.events.EventMetadata;
import com.vasmarketing.events.merchant.MerchantReference;
import com.vasmarketing.platform.kafka.OutboxMessage;
import com.vasmarketing.platform.kafka.OutboxWriter;
import com.vasmarketing.platform.types.UuidV7;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/** Writes the merchant snapshot to compacted {@code merchant.reference} (key = merchantId). */
@Component
class OutboxMerchantReferencePublisher implements MerchantReferencePublisher {

  static final String TOPIC = "merchant.reference";

  private final OutboxWriter outbox;
  private final Clock clock;

  OutboxMerchantReferencePublisher(OutboxWriter outbox, Clock clock) {
    this.outbox = outbox;
    this.clock = clock;
  }

  @Override
  public void publish(Merchant merchant) {
    UUID eventId = UuidV7.generate();
    MerchantReference record =
        MerchantReference.newBuilder()
            .setMetadata(
                EventMetadata.newBuilder()
                    .setEventId(eventId.toString())
                    .setEventType("MerchantUpdated")
                    .setSource("customer-service")
                    .setOccurredAt(merchant.updatedAt())
                    .setProducedAt(clock.instant())
                    .setCorrelationId(MDC.get("correlationId"))
                    .build())
            .setMerchantId(merchant.id().toString())
            .setName(merchant.name())
            .setMcc(merchant.mcc().value())
            .setCategory(merchant.category().name())
            .setCountryCode(merchant.countryCode())
            .setActive(merchant.active())
            .build();
    outbox.append(
        List.of(
            new OutboxMessage(
                TOPIC, merchant.id().toString(), eventId, "MerchantUpdated", record)));
  }
}
