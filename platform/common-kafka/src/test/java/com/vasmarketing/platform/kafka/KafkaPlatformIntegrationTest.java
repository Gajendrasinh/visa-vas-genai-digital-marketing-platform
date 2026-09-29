package com.vasmarketing.platform.kafka;

import static com.vasmarketing.platform.kafka.KafkaTestApplication.TOPIC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.vasmarketing.events.EventMetadata;
import com.vasmarketing.events.merchant.MerchantReference;
import com.vasmarketing.platform.kafka.KafkaTestApplication.MerchantListener;
import com.vasmarketing.platform.testsupport.ServiceKafka;
import com.vasmarketing.platform.testsupport.ServicePostgres;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class KafkaPlatformIntegrationTest {

  @DynamicPropertySource
  static void infrastructure(DynamicPropertyRegistry registry) {
    ServicePostgres.register(registry, "kafkait");
    registry.add("platform.persistence.schema", () -> "kafkait");
    ServiceKafka.register(registry);
    registry.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
    registry.add("spring.kafka.listener.ack-mode", () -> "record");
    registry.add("platform.kafka.retry.initial-interval", () -> "100ms");
    registry.add("platform.kafka.retry.max-attempts", () -> "3");
    registry.add("platform.kafka.outbox.send-timeout", () -> "12s");
    registry.add("spring.kafka.producer.properties.max.block.ms", () -> "5000");
    registry.add("spring.kafka.producer.properties.request.timeout.ms", () -> "5000");
    registry.add("spring.kafka.producer.properties.delivery.timeout.ms", () -> "10000");
  }

  @Autowired private OutboxWriter outbox;
  @Autowired private OutboxPublisher publisher;
  @Autowired private TransactionTemplate transactions;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private KafkaTemplate<String, SpecificRecord> kafka;
  @Autowired private DlqReplayer replayer;

  private static MerchantReference merchant(String eventId, String merchantId, String name) {
    Instant now = Instant.now();
    return MerchantReference.newBuilder()
        .setMetadata(
            EventMetadata.newBuilder()
                .setEventId(eventId)
                .setEventType("MerchantUpdated")
                .setSource("kafka-it")
                .setOccurredAt(now)
                .setProducedAt(now)
                .build())
        .setMerchantId(merchantId)
        .setName(name)
        .setMcc("4511")
        .setCategory("TRAVEL")
        .setCountryCode("US")
        .setActive(true)
        .build();
  }

  private static OutboxMessage message(String merchantId, String name) {
    UUID eventId = UUID.randomUUID();
    return new OutboxMessage(
        TOPIC,
        merchantId,
        eventId,
        "MerchantUpdated",
        merchant(eventId.toString(), merchantId, name));
  }

  private long unpublished() {
    Long count =
        jdbc.queryForObject(
            "select count(*) from outbox_events where published_at is null", Long.class);
    return count == null ? 0 : count;
  }

  @Test
  void onlyCommittedEventsArePublishedInOrder() {
    String key = UUID.randomUUID().toString();
    String prefix = "ordered-" + key.substring(0, 8) + "-";
    transactions.executeWithoutResult(
        s -> outbox.append(List.of(message(key, prefix + 1), message(key, prefix + 2))));
    transactions.executeWithoutResult(
        s -> {
          outbox.append(List.of(message(key, prefix + "rolled-back")));
          s.setRollbackOnly();
        });
    transactions.executeWithoutResult(s -> outbox.append(List.of(message(key, prefix + 3))));

    await()
        .atMost(Duration.ofSeconds(60))
        .untilAsserted(
            () ->
                assertThat(MerchantListener.APPLIED.stream().filter(n -> n.startsWith(prefix)))
                    .containsExactly(prefix + 1, prefix + 2, prefix + 3));
  }

  @Test
  void outboxWritesRequireATransaction() {
    assertThatThrownBy(() -> outbox.append(List.of(message("k", "no-tx"))))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void brokerOutageDelaysButNeverLosesEvents() {
    String name = "outage-" + UUID.randomUUID();
    ServiceKafka.pauseBroker();
    try {
      transactions.executeWithoutResult(
          s -> outbox.append(List.of(message(UUID.randomUUID().toString(), name))));
      assertThat(publisher.publishBatch()).isZero();
      assertThat(unpublished()).isPositive();
      Integer attempts =
          jdbc.queryForObject(
              "select max(attempts) from outbox_events where published_at is null", Integer.class);
      assertThat(attempts).isPositive();
    } finally {
      ServiceKafka.resumeBroker();
    }

    await()
        .atMost(Duration.ofSeconds(90))
        .untilAsserted(() -> assertThat(MerchantListener.APPLIED).contains(name));
    assertThat(MerchantListener.APPLIED.stream().filter(name::equals)).hasSize(1);
    assertThat(publisher.oldestUnpublishedAgeSeconds()).isGreaterThanOrEqualTo(0);
  }

  @Test
  void duplicateDeliveryIsAppliedOnce() throws Exception {
    String eventId = UUID.randomUUID().toString();
    String name = "duplicate-" + eventId;
    MerchantReference event = merchant(eventId, "m-dup", name);
    kafka.send(TOPIC, "m-dup", event).get();
    kafka.send(TOPIC, "m-dup", event).get();

    await()
        .atMost(Duration.ofSeconds(60))
        .untilAsserted(
            () ->
                assertThat(MerchantListener.ATTEMPTS.stream().filter(eventId::equals)).hasSize(2));
    assertThat(MerchantListener.APPLIED.stream().filter(name::equals)).hasSize(1);
  }

  @Test
  void poisonMessagesAreDeadLetteredAfterRetriesAndCanBeReplayed() throws Exception {
    String eventId = UUID.randomUUID().toString();
    kafka.send(TOPIC, "m-poison", merchant(eventId, "m-poison", "poison-" + eventId)).get();

    ConsumerRecord<byte[], byte[]> dead =
        awaitDlqRecord(r -> new String(r.value(), StandardCharsets.ISO_8859_1).contains(eventId));
    assertThat(MerchantListener.ATTEMPTS.stream().filter(eventId::equals)).hasSize(3);
    assertThat(
            new String(
                dead.headers().lastHeader(KafkaHeaders.DLT_EXCEPTION_MESSAGE).value(),
                StandardCharsets.UTF_8))
        .contains("cannot apply");
    assertThat(new String(dead.key(), StandardCharsets.UTF_8)).isEqualTo("m-poison");

    int replayed = replayer.replay(TOPIC + ".dlq", dead.partition(), dead.offset(), dead.offset());
    assertThat(replayed).isEqualTo(1);
    await()
        .atMost(Duration.ofSeconds(60))
        .untilAsserted(
            () ->
                assertThat(MerchantListener.ATTEMPTS.stream().filter(eventId::equals))
                    .hasSizeGreaterThan(3));
  }

  @Test
  void undeserializableRecordsGoStraightToTheDlq() throws Exception {
    String marker = "not-avro-" + UUID.randomUUID();
    try (KafkaProducer<byte[], byte[]> raw =
        new KafkaProducer<>(
            Map.of(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, ServiceKafka.bootstrapServers()),
            new ByteArraySerializer(),
            new ByteArraySerializer())) {
      raw.send(
              new ProducerRecord<>(
                  TOPIC,
                  "m-raw".getBytes(StandardCharsets.UTF_8),
                  marker.getBytes(StandardCharsets.UTF_8)))
          .get();
    }

    ConsumerRecord<byte[], byte[]> dead =
        awaitDlqRecord(r -> new String(r.value(), StandardCharsets.UTF_8).equals(marker));
    assertThat(dead.headers().lastHeader(KafkaHeaders.DLT_EXCEPTION_FQCN)).isNotNull();
  }

  private static ConsumerRecord<byte[], byte[]> awaitDlqRecord(
      java.util.function.Predicate<ConsumerRecord<byte[], byte[]>> match) {
    try (KafkaConsumer<byte[], byte[]> consumer =
        new KafkaConsumer<>(
            Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                ServiceKafka.bootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG,
                "dlq-reader-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "earliest"),
            new ByteArrayDeserializer(),
            new ByteArrayDeserializer())) {
      consumer.subscribe(List.of(TOPIC + ".dlq"));
      long deadline = System.nanoTime() + Duration.ofSeconds(90).toNanos();
      while (System.nanoTime() < deadline) {
        for (ConsumerRecord<byte[], byte[]> record : consumer.poll(Duration.ofMillis(500))) {
          if (match.test(record)) {
            return record;
          }
        }
      }
    }
    throw new AssertionError("no matching record arrived on " + TOPIC + ".dlq");
  }
}
