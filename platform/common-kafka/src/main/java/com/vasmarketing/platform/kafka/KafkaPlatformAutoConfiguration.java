package com.vasmarketing.platform.kafka;

import com.vasmarketing.platform.persistence.PlatformPersistenceAutoConfiguration;
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.time.Clock;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.apache.avro.AvroRuntimeException;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.support.serializer.DelegatingByTypeSerializer;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Platform Kafka conventions for every service: Avro + Schema Registry serde, idempotent producer,
 * bounded blocking retry then {@code <topic>.dlq}, correlation propagation, and (for services with
 * a database) the transactional outbox and consumer dedup.
 */
@AutoConfiguration(
    before = KafkaAutoConfiguration.class,
    after = {DataSourceAutoConfiguration.class, PlatformPersistenceAutoConfiguration.class})
@EnableConfigurationProperties(KafkaPlatformProperties.class)
public class KafkaPlatformAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  DefaultKafkaProducerFactory<String, SpecificRecord> platformProducerFactory(
      KafkaProperties kafka, KafkaPlatformProperties platform) {
    Map<String, Object> props = new HashMap<>(kafka.buildProducerProperties());
    props.putAll(registry(platform));
    props.put(ProducerConfig.ACKS_CONFIG, "all");
    props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
    props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "zstd");
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class);
    return new DefaultKafkaProducerFactory<>(props);
  }

  @Bean
  @ConditionalOnMissingBean
  KafkaTemplate<String, SpecificRecord> platformKafkaTemplate(
      DefaultKafkaProducerFactory<String, SpecificRecord> producerFactory) {
    return new KafkaTemplate<>(producerFactory);
  }

  @Bean
  @ConditionalOnMissingBean
  ConsumerFactory<Object, Object> platformConsumerFactory(
      KafkaProperties kafka, KafkaPlatformProperties platform) {
    Map<String, Object> props = new HashMap<>(kafka.buildConsumerProperties());
    props.putAll(registry(platform));
    props.put(KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG, true);
    props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
    // Undeserializable records become DeserializationExceptions routed to the DLQ, not poison
    // pills.
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
    props.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class);
    props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, KafkaAvroDeserializer.class);
    return new DefaultKafkaConsumerFactory<>(props);
  }

  /** Blocking exponential retry, then publish to {@code <topic>.dlq} keeping the original key. */
  @Bean
  @ConditionalOnMissingBean(CommonErrorHandler.class)
  DefaultErrorHandler platformErrorHandler(
      KafkaProperties kafka, KafkaPlatformProperties platform) {
    Map<String, Object> props = new HashMap<>(kafka.buildProducerProperties());
    props.putAll(registry(platform));
    KafkaAvroSerializer avro = new KafkaAvroSerializer();
    avro.configure(props, false);
    Map<Class<?>, Serializer<?>> byType = new LinkedHashMap<>();
    byType.put(byte[].class, new ByteArraySerializer());
    byType.put(String.class, new StringSerializer());
    byType.put(SpecificRecord.class, avro);
    KafkaTemplate<Object, Object> dlqTemplate =
        new KafkaTemplate<>(
            new DefaultKafkaProducerFactory<>(
                props,
                new DelegatingByTypeSerializer(byType, true),
                new DelegatingByTypeSerializer(byType, true)));
    DeadLetterPublishingRecoverer recoverer =
        new DeadLetterPublishingRecoverer(
            dlqTemplate, (record, ex) -> new TopicPartition(record.topic() + ".dlq", -1));
    KafkaPlatformProperties.Retry retry = platform.retry();
    ExponentialBackOffWithMaxRetries backOff =
        new ExponentialBackOffWithMaxRetries(retry.maxAttempts() - 1);
    backOff.setInitialInterval(retry.initialInterval().toMillis());
    backOff.setMultiplier(retry.multiplier());
    backOff.setMaxInterval(retry.maxInterval().toMillis());
    DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
    // Retrying cannot fix malformed or invalid data.
    handler.addNotRetryableExceptions(IllegalArgumentException.class, AvroRuntimeException.class);
    return handler;
  }

  @Bean
  @ConditionalOnMissingBean
  CorrelationRecordInterceptor correlationRecordInterceptor() {
    return new CorrelationRecordInterceptor();
  }

  @Bean
  @ConditionalOnMissingBean
  DlqReplayer dlqReplayer(KafkaProperties kafka) {
    return new DlqReplayer(kafka.buildAdminProperties());
  }

  private static Map<String, Object> registry(KafkaPlatformProperties platform) {
    return Map.of(
        AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, platform.schemaRegistryUrl(),
        AbstractKafkaSchemaSerDeConfig.AUTO_REGISTER_SCHEMAS, platform.autoRegisterSchemas());
  }

  /** Outbox and consumer dedup for services that own a database. */
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnBean(DataSource.class)
  @EnableScheduling
  static class OutboxConfiguration {

    @Bean
    @ConditionalOnMissingBean
    OutboxWriter outboxWriter(DataSource dataSource) {
      return new OutboxWriter(new JdbcTemplate(dataSource));
    }

    @Bean
    @ConditionalOnMissingBean
    ProcessedEvents processedEvents(DataSource dataSource) {
      return new ProcessedEvents(new JdbcTemplate(dataSource));
    }

    @Bean
    @ConditionalOnMissingBean
    OutboxPublisher outboxPublisher(
        DataSource dataSource,
        PlatformTransactionManager transactionManager,
        KafkaTemplate<String, SpecificRecord> kafka,
        KafkaPlatformProperties platform,
        @Value("${spring.application.name:service}") String serviceName) {
      return new OutboxPublisher(
          new JdbcTemplate(dataSource),
          new TransactionTemplate(transactionManager),
          kafka,
          Clock.systemUTC(),
          serviceName,
          platform.outbox().batchSize(),
          platform.outbox().sendTimeout());
    }

    @Bean
    @ConditionalOnProperty(
        name = "platform.kafka.outbox.enabled",
        havingValue = "true",
        matchIfMissing = true)
    SchedulingConfigurer outboxSchedule(
        OutboxPublisher publisher, KafkaPlatformProperties platform) {
      return registrar -> {
        registrar.addFixedDelayTask(publisher::publishBatch, platform.outbox().pollInterval());
        registrar.addFixedDelayTask(
            () -> publisher.purgePublished(platform.outbox().retention()),
            java.time.Duration.ofHours(1));
      };
    }

    /** Registered by Actuator when Micrometer is present; alert when the age keeps growing. */
    @Bean
    @ConditionalOnClass(MeterBinder.class)
    MeterBinder outboxMetrics(OutboxPublisher publisher) {
      return registry ->
          Gauge.builder(
                  "outbox.oldest.unpublished.age",
                  publisher,
                  OutboxPublisher::oldestUnpublishedAgeSeconds)
              .description("Age of the oldest event not yet published to Kafka")
              .baseUnit("seconds")
              .register(registry);
    }
  }
}
