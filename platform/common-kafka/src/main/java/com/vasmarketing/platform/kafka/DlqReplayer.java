package com.vasmarketing.platform.kafka;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.support.KafkaHeaders;

/**
 * Re-publishes dead-lettered records, byte for byte, to the topic they came from (after the cause
 * is fixed). Keys and headers are preserved, so ordering and correlation survive the replay.
 */
public final class DlqReplayer {

  private static final Logger log = LoggerFactory.getLogger(DlqReplayer.class);
  private static final Duration POLL = Duration.ofMillis(500);

  private final Map<String, Object> clientProperties;

  public DlqReplayer(Map<String, Object> clientProperties) {
    this.clientProperties = Map.copyOf(clientProperties);
  }

  /** Replays records of one DLQ partition with offsets in [fromOffset, toOffset]. */
  public int replay(String dlqTopic, int partition, long fromOffset, long toOffset) {
    TopicPartition tp = new TopicPartition(dlqTopic, partition);
    int replayed = 0;
    try (KafkaConsumer<byte[], byte[]> consumer = consumer();
        KafkaProducer<byte[], byte[]> producer = producer()) {
      consumer.assign(List.of(tp));
      consumer.seek(tp, fromOffset);
      long position = fromOffset;
      int idlePolls = 0;
      while (position <= toOffset && idlePolls < 3) {
        var records = consumer.poll(POLL).records(tp);
        idlePolls = records.isEmpty() ? idlePolls + 1 : 0;
        for (ConsumerRecord<byte[], byte[]> record : records) {
          if (record.offset() > toOffset) {
            break;
          }
          producer.send(toOriginalTopic(record)).get();
          replayed++;
          position = record.offset() + 1;
        }
      }
      producer.flush();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("replay interrupted", e);
    } catch (java.util.concurrent.ExecutionException e) {
      throw new IllegalStateException("replay failed", e);
    }
    log.info(
        "Replayed {} record(s) from {}-{} offsets {}..{}",
        replayed,
        dlqTopic,
        partition,
        fromOffset,
        toOffset);
    return replayed;
  }

  private static ProducerRecord<byte[], byte[]> toOriginalTopic(
      ConsumerRecord<byte[], byte[]> record) {
    Header original = record.headers().lastHeader(KafkaHeaders.DLT_ORIGINAL_TOPIC);
    String topic =
        original != null
            ? new String(original.value(), StandardCharsets.UTF_8)
            : record.topic().replaceFirst("\\.dlq$", "");
    ProducerRecord<byte[], byte[]> replay =
        new ProducerRecord<>(topic, record.key(), record.value());
    for (Header header : record.headers()) {
      if (!header.key().startsWith("kafka_dlt-")) {
        replay.headers().add(header);
      }
    }
    return replay;
  }

  private KafkaConsumer<byte[], byte[]> consumer() {
    Map<String, Object> props = new java.util.HashMap<>(clientProperties);
    props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
    props.remove(ConsumerConfig.GROUP_ID_CONFIG);
    return new KafkaConsumer<>(props, new ByteArrayDeserializer(), new ByteArrayDeserializer());
  }

  private KafkaProducer<byte[], byte[]> producer() {
    Map<String, Object> props = new java.util.HashMap<>(clientProperties);
    props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
    props.put(ProducerConfig.ACKS_CONFIG, "all");
    return new KafkaProducer<>(props, new ByteArraySerializer(), new ByteArraySerializer());
  }
}
