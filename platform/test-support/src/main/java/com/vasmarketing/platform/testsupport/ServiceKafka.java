package com.vasmarketing.platform.testsupport;

import java.time.Duration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.kafka.KafkaContainer;

/**
 * One Kafka broker and one Schema Registry per test JVM, on a shared network, using the same images
 * as docker-compose. Also lets tests simulate a broker outage.
 */
public final class ServiceKafka {

  private static final Network NETWORK = Network.newNetwork();

  private static final KafkaContainer KAFKA =
      new KafkaContainer("apache/kafka:4.3.1")
          .withNetwork(NETWORK)
          .withListener("kafka:19092")
          .withEnv("KAFKA_HEAP_OPTS", "-Xms256m -Xmx384m");

  private static final GenericContainer<?> SCHEMA_REGISTRY =
      new GenericContainer<>("confluentinc/cp-schema-registry:8.2.4")
          .withNetwork(NETWORK)
          .withExposedPorts(8081)
          .withEnv("SCHEMA_REGISTRY_HOST_NAME", "schema-registry")
          .withEnv("SCHEMA_REGISTRY_LISTENERS", "http://0.0.0.0:8081")
          .withEnv("SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS", "PLAINTEXT://kafka:19092")
          .withEnv("SCHEMA_REGISTRY_SCHEMA_COMPATIBILITY_LEVEL", "backward_transitive")
          .withEnv("SCHEMA_REGISTRY_HEAP_OPTS", "-Xms128m -Xmx256m")
          .waitingFor(
              Wait.forHttp("/subjects")
                  .forStatusCode(200)
                  .withStartupTimeout(Duration.ofMinutes(3)));

  private ServiceKafka() {}

  private static final Object LOCK = new Object();

  public static void start() {
    synchronized (LOCK) {
      if (!KAFKA.isRunning()) {
        KAFKA.start();
      }
      if (!SCHEMA_REGISTRY.isRunning()) {
        SCHEMA_REGISTRY.start();
      }
    }
  }

  /** Registers bootstrap servers and the Schema Registry URL for a Spring test context. */
  public static void register(DynamicPropertyRegistry registry) {
    start();
    registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    registry.add("platform.kafka.schema-registry-url", ServiceKafka::schemaRegistryUrl);
  }

  public static String bootstrapServers() {
    start();
    return KAFKA.getBootstrapServers();
  }

  public static String schemaRegistryUrl() {
    start();
    return "http://" + SCHEMA_REGISTRY.getHost() + ":" + SCHEMA_REGISTRY.getMappedPort(8081);
  }

  /** Freezes the broker process (network stays up but nothing is served), simulating an outage. */
  public static void pauseBroker() {
    DockerClientFactory.instance().client().pauseContainerCmd(KAFKA.getContainerId()).exec();
  }

  public static void resumeBroker() {
    DockerClientFactory.instance().client().unpauseContainerCmd(KAFKA.getContainerId()).exec();
  }
}
