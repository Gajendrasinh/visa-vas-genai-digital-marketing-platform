package com.vasmarketing.campaign.infrastructure.messaging;

import static com.vasmarketing.campaign.CampaignFixtures.APPROVER;
import static com.vasmarketing.campaign.CampaignFixtures.CREATOR;
import static com.vasmarketing.campaign.CampaignFixtures.details;
import static com.vasmarketing.campaign.CampaignFixtures.email;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.vasmarketing.campaign.application.CampaignAuthoringService;
import com.vasmarketing.campaign.application.CampaignLifecycleService;
import com.vasmarketing.campaign.application.CreateCampaignCommand;
import com.vasmarketing.campaign.domain.model.Campaign;
import com.vasmarketing.campaign.domain.model.Channel;
import com.vasmarketing.campaign.domain.model.ContentAuthor;
import com.vasmarketing.campaign.domain.model.VariantId;
import com.vasmarketing.events.campaign.CampaignLifecycleEvent;
import com.vasmarketing.events.campaign.CampaignTransition;
import com.vasmarketing.platform.testsupport.ServiceKafka;
import com.vasmarketing.platform.testsupport.ServicePostgres;
import com.vasmarketing.platform.types.DomainRuleViolation;
import com.vasmarketing.platform.types.Precondition;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Campaign events go through the transactional outbox and reach Kafka as schema-registered Avro,
 * keyed by campaign id, in lifecycle order.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CampaignOutboxIntegrationTest {

  @DynamicPropertySource
  static void infrastructure(DynamicPropertyRegistry registry) {
    ServicePostgres.register(registry, "campaign");
    ServiceKafka.register(registry);
    registry.add("platform.kafka.outbox.enabled", () -> "true");
  }

  @Autowired private CampaignAuthoringService authoring;
  @Autowired private CampaignLifecycleService lifecycle;
  @Autowired private JdbcTemplate jdbc;

  private Campaign publishedCampaign() {
    Campaign campaign =
        authoring.create(
            CREATOR,
            CreateCampaignCommand.manual(details("Outbox " + UUID.randomUUID(), Channel.EMAIL)));
    VariantId variant =
        authoring.addVariant(
            CREATOR, campaign.id(), email("A"), ContentAuthor.HUMAN, null, Precondition.none());
    authoring.recordCompliance(CREATOR, campaign.id(), variant, true, "pass", Precondition.none());
    lifecycle.submit(CREATOR, campaign.id(), Precondition.none());
    lifecycle.approve(APPROVER, campaign.id(), "ok", Precondition.none());
    lifecycle.publish(APPROVER, campaign.id(), Precondition.none());
    return campaign;
  }

  @Test
  void lifecycleEventsAreWrittenToTheOutboxInOrderWithSnapshots() {
    Campaign campaign = publishedCampaign();

    List<CampaignLifecycleEvent> events =
        jdbc.query(
            "select payload from outbox_events where message_key = ? order by created_at, id",
            (rs, n) -> decode(rs.getBytes("payload")),
            campaign.id().toString());

    assertThat(events)
        .extracting(CampaignLifecycleEvent::getTransition)
        .containsExactly(
            CampaignTransition.CREATED,
            CampaignTransition.SUBMITTED,
            CampaignTransition.APPROVED,
            CampaignTransition.PUBLISHED);
    CampaignLifecycleEvent published = events.getLast();
    assertThat(published.getStatus()).isEqualTo("SCHEDULED");
    assertThat(published.getActor()).isEqualTo("approver");
    assertThat(published.getApprovedVariantIds()).hasSize(1);
    assertThat(published.getMetadata().getTenantId()).isEqualTo(CREATOR.tenantId().toString());
  }

  @Test
  void rejectedOperationsWriteNoEvents() {
    Campaign campaign =
        authoring.create(
            CREATOR,
            CreateCampaignCommand.manual(details("NoEvent " + UUID.randomUUID(), Channel.EMAIL)));

    assertThatThrownBy(() -> lifecycle.publish(CREATOR, campaign.id(), Precondition.none()))
        .isInstanceOf(DomainRuleViolation.class);

    Long count =
        jdbc.queryForObject(
            "select count(*) from outbox_events where message_key = ?",
            Long.class,
            campaign.id().toString());
    assertThat(count).isEqualTo(1); // only CampaignCreated
  }

  @Test
  void eventsReachKafkaAsRegisteredAvroKeyedByCampaign() {
    Campaign campaign = publishedCampaign();
    String key = campaign.id().toString();
    List<CampaignTransition> seen = new ArrayList<>();

    try (KafkaConsumer<String, Object> consumer = consumer()) {
      consumer.subscribe(List.of(OutboxCampaignEventPublisher.TOPIC));
      await()
          .atMost(Duration.ofSeconds(90))
          .pollInterval(Duration.ofMillis(200))
          .untilAsserted(
              () -> {
                for (ConsumerRecord<String, Object> record :
                    consumer.poll(Duration.ofMillis(300))) {
                  if (key.equals(record.key())) {
                    seen.add(((CampaignLifecycleEvent) record.value()).getTransition());
                  }
                }
                assertThat(seen).hasSize(4);
              });
    }
    assertThat(seen)
        .containsExactly(
            CampaignTransition.CREATED,
            CampaignTransition.SUBMITTED,
            CampaignTransition.APPROVED,
            CampaignTransition.PUBLISHED);
  }

  private static KafkaConsumer<String, Object> consumer() {
    KafkaAvroDeserializer values = new KafkaAvroDeserializer();
    values.configure(
        Map.of(
            KafkaAvroDeserializerConfig.SCHEMA_REGISTRY_URL_CONFIG,
            ServiceKafka.schemaRegistryUrl(),
            KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG,
            true),
        false);
    return new KafkaConsumer<>(
        Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
            ServiceKafka.bootstrapServers(),
            ConsumerConfig.GROUP_ID_CONFIG,
            "campaign-outbox-it-" + UUID.randomUUID(),
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
            "earliest"),
        new StringDeserializer(),
        values);
  }

  private static CampaignLifecycleEvent decode(byte[] payload) {
    try {
      return new SpecificDatumReader<>(CampaignLifecycleEvent.class)
          .read(null, DecoderFactory.get().binaryDecoder(payload, null));
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }
}
