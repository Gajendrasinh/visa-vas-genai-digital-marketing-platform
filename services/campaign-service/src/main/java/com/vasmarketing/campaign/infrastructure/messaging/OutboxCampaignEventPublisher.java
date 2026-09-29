package com.vasmarketing.campaign.infrastructure.messaging;

import com.vasmarketing.campaign.domain.event.CampaignEvent;
import com.vasmarketing.campaign.domain.model.Campaign;
import com.vasmarketing.campaign.domain.model.VariantStatus;
import com.vasmarketing.campaign.domain.port.CampaignEventPublisher;
import com.vasmarketing.events.EventMetadata;
import com.vasmarketing.events.campaign.CampaignLifecycleEvent;
import com.vasmarketing.events.campaign.CampaignTransition;
import com.vasmarketing.platform.kafka.OutboxMessage;
import com.vasmarketing.platform.kafka.OutboxWriter;
import com.vasmarketing.platform.types.UuidV7;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/** Maps campaign domain events to {@code campaign.events} records in the transactional outbox. */
@Component
class OutboxCampaignEventPublisher implements CampaignEventPublisher {

  static final String TOPIC = "campaign.events";
  private static final String SOURCE = "campaign-service";

  private final OutboxWriter outbox;
  private final Clock clock;

  OutboxCampaignEventPublisher(OutboxWriter outbox, Clock clock) {
    this.outbox = outbox;
    this.clock = clock;
  }

  @Override
  public void publish(Campaign campaign, List<CampaignEvent> events) {
    outbox.append(events.stream().map(event -> toMessage(campaign, event)).toList());
  }

  private OutboxMessage toMessage(Campaign campaign, CampaignEvent event) {
    UUID eventId = UuidV7.generate();
    String eventType = event.getClass().getSimpleName();
    CampaignLifecycleEvent record =
        CampaignLifecycleEvent.newBuilder()
            .setMetadata(
                EventMetadata.newBuilder()
                    .setEventId(eventId.toString())
                    .setEventType(eventType)
                    .setSource(SOURCE)
                    .setTenantId(campaign.tenantId().toString())
                    .setOccurredAt(event.occurredAt())
                    .setProducedAt(clock.instant())
                    .setCorrelationId(MDC.get("correlationId"))
                    .build())
            .setCampaignId(campaign.id().toString())
            .setTransition(transition(event))
            .setStatus(campaign.status().name())
            .setActor(event.actor().value())
            .setName(campaign.details().name())
            .setSegmentId(campaign.details().segmentId().toString())
            .setOfferId(campaign.details().offerId().toString())
            .setStartAt(campaign.details().schedule().startAt())
            .setEndAt(campaign.details().schedule().endAt())
            .setChannels(campaign.details().channels().stream().map(Enum::name).sorted().toList())
            .setApprovedVariantIds(
                campaign.variants().stream()
                    .filter(v -> v.status() == VariantStatus.APPROVED)
                    .map(v -> v.id().value().toString())
                    .toList())
            .setReason(
                event instanceof CampaignEvent.CampaignRejected rejected ? rejected.reason() : null)
            .build();
    return new OutboxMessage(TOPIC, campaign.id().toString(), eventId, eventType, record);
  }

  private static CampaignTransition transition(CampaignEvent event) {
    return switch (event) {
      case CampaignEvent.CampaignCreated e -> CampaignTransition.CREATED;
      case CampaignEvent.CampaignSubmitted e -> CampaignTransition.SUBMITTED;
      case CampaignEvent.CampaignApproved e -> CampaignTransition.APPROVED;
      case CampaignEvent.CampaignRejected e -> CampaignTransition.REJECTED;
      case CampaignEvent.CampaignPublished e -> CampaignTransition.PUBLISHED;
      case CampaignEvent.CampaignActivated e -> CampaignTransition.ACTIVATED;
      case CampaignEvent.CampaignPaused e -> CampaignTransition.PAUSED;
      case CampaignEvent.CampaignResumed e -> CampaignTransition.RESUMED;
      case CampaignEvent.CampaignCompleted e -> CampaignTransition.COMPLETED;
      case CampaignEvent.CampaignArchived e -> CampaignTransition.ARCHIVED;
    };
  }
}
