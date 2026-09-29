package com.vasmarketing.offer.infrastructure.messaging;

import com.vasmarketing.events.EventMetadata;
import com.vasmarketing.events.offer.OfferChange;
import com.vasmarketing.events.offer.OfferLifecycleEvent;
import com.vasmarketing.offer.domain.model.Offer;
import com.vasmarketing.offer.domain.model.OfferLifecycleChange;
import com.vasmarketing.offer.domain.port.OfferEventPublisher;
import com.vasmarketing.platform.kafka.OutboxMessage;
import com.vasmarketing.platform.kafka.OutboxWriter;
import com.vasmarketing.platform.types.UuidV7;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/** Writes {@code offer.events} records (key = offerId) to the transactional outbox. */
@Component
class OutboxOfferEventPublisher implements OfferEventPublisher {

  static final String TOPIC = "offer.events";

  private final OutboxWriter outbox;
  private final Clock clock;

  OutboxOfferEventPublisher(OutboxWriter outbox, Clock clock) {
    this.outbox = outbox;
    this.clock = clock;
  }

  @Override
  public void publish(Offer offer, OfferLifecycleChange change) {
    UUID eventId = UuidV7.generate();
    String eventType = "Offer" + pascal(change.name());
    OfferLifecycleEvent record =
        OfferLifecycleEvent.newBuilder()
            .setMetadata(
                EventMetadata.newBuilder()
                    .setEventId(eventId.toString())
                    .setEventType(eventType)
                    .setSource("offer-service")
                    .setTenantId(offer.tenantId().toString())
                    .setOccurredAt(offer.updatedAt())
                    .setProducedAt(clock.instant())
                    .setCorrelationId(MDC.get("correlationId"))
                    .build())
            .setOfferId(offer.id().toString())
            .setChange(OfferChange.valueOf(change.name()))
            .setStatus(offer.status().name())
            .setMerchantId(offer.terms().merchantId().toString())
            .setTitle(offer.terms().title())
            .setCategory(offer.terms().category().name())
            .setStartAt(offer.terms().validity().startAt())
            .setEndAt(offer.terms().validity().endAt())
            .setRuleSetVersion(offer.ruleSetVersion())
            .build();
    outbox.append(
        List.of(new OutboxMessage(TOPIC, offer.id().toString(), eventId, eventType, record)));
  }

  private static String pascal(String upperSnake) {
    StringBuilder out = new StringBuilder();
    for (String part : upperSnake.split("_")) {
      out.append(part.charAt(0)).append(part.substring(1).toLowerCase(java.util.Locale.ROOT));
    }
    return out.toString();
  }
}
