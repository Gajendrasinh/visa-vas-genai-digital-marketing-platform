package com.vasmarketing.platform.kafka;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Publishes outbox rows in {@code (created_at, id)} order. A transaction-scoped Postgres advisory
 * lock elects one publisher per service at a time, which keeps per-key order across instances.
 * Delivery is at-least-once: a crash between send and commit republishes, and consumers deduplicate
 * by eventId. A failed send stops the batch so later events never overtake it.
 */
public final class OutboxPublisher {

  private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

  private final JdbcTemplate jdbc;
  private final TransactionTemplate transactions;
  private final KafkaTemplate<String, SpecificRecord> kafka;
  private final Clock clock;
  private final long lockKey;
  private final int batchSize;
  private final Duration sendTimeout;

  record Row(
      UUID id,
      String topic,
      String key,
      String eventType,
      String payloadClass,
      byte[] payload,
      String correlationId) {}

  public OutboxPublisher(
      JdbcTemplate jdbc,
      TransactionTemplate transactions,
      KafkaTemplate<String, SpecificRecord> kafka,
      Clock clock,
      String serviceName,
      int batchSize,
      Duration sendTimeout) {
    this.jdbc = jdbc;
    this.transactions = transactions;
    this.kafka = kafka;
    this.clock = clock;
    this.lockKey = ("outbox:" + serviceName).hashCode();
    this.batchSize = batchSize;
    this.sendTimeout = sendTimeout;
  }

  /** Publishes one batch; returns the number of events published (0 if another leader holds it). */
  public int publishBatch() {
    Integer published = transactions.execute(status -> leader() ? publishLocked() : 0);
    return published == null ? 0 : published;
  }

  private boolean leader() {
    return Boolean.TRUE.equals(
        jdbc.queryForObject("select pg_try_advisory_xact_lock(?)", Boolean.class, lockKey));
  }

  private int publishLocked() {
    List<Row> rows =
        jdbc.query(
            "select id, topic, message_key, event_type, payload_class, payload, correlation_id from outbox_events"
                + " where published_at is null order by created_at, id limit ?",
            (rs, n) ->
                new Row(
                    rs.getObject("id", UUID.class),
                    rs.getString("topic"),
                    rs.getString("message_key"),
                    rs.getString("event_type"),
                    rs.getString("payload_class"),
                    rs.getBytes("payload"),
                    rs.getString("correlation_id")),
            batchSize);
    int published = 0;
    for (Row row : rows) {
      try {
        send(row);
      } catch (Exception e) {
        recordFailure(row, e);
        break; // preserve order: never publish later events before this one
      }
      jdbc.update(
          "update outbox_events set published_at = ? where id = ?",
          Timestamp.from(clock.instant()),
          row.id());
      published++;
    }
    return published;
  }

  private void send(Row row) throws Exception {
    ProducerRecord<String, SpecificRecord> record =
        new ProducerRecord<>(
            row.topic(), row.key(), AvroBinary.decode(row.payloadClass(), row.payload()));
    record.headers().add(Headers.EVENT_ID, row.id().toString().getBytes(StandardCharsets.UTF_8));
    record.headers().add(Headers.EVENT_TYPE, row.eventType().getBytes(StandardCharsets.UTF_8));
    if (row.correlationId() != null) {
      record
          .headers()
          .add(Headers.CORRELATION_ID, row.correlationId().getBytes(StandardCharsets.UTF_8));
    }
    kafka.send(record).get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS);
  }

  private void recordFailure(Row row, Exception e) {
    String message = e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage());
    log.warn("Outbox publish of event {} to {} failed; will retry", row.id(), row.topic(), e);
    jdbc.update(
        "update outbox_events set attempts = attempts + 1, last_error = ? where id = ?",
        message.length() > 1000 ? message.substring(0, 1000) : message,
        row.id());
  }

  /** Age of the oldest unpublished event in seconds (0 if none); alert when it grows. */
  public double oldestUnpublishedAgeSeconds() {
    Double age =
        jdbc.queryForObject(
            "select coalesce(extract(epoch from (now() - min(created_at))), 0) from outbox_events where published_at is null",
            Double.class);
    return age == null ? 0 : age;
  }

  /** Deletes rows published longer ago than {@code retention}. */
  public int purgePublished(Duration retention) {
    return jdbc.update(
        "delete from outbox_events where published_at < ?",
        Timestamp.from(clock.instant().minus(retention)));
  }
}
