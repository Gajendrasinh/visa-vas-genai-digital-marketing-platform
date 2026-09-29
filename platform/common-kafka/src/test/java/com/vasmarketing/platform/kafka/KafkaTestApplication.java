package com.vasmarketing.platform.kafka;

import com.vasmarketing.events.merchant.MerchantReference;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@SpringBootConfiguration
@EnableAutoConfiguration
@Import(KafkaTestApplication.MerchantListener.class)
public class KafkaTestApplication {

  static final String TOPIC = "test.merchant.reference";
  static final String GROUP = "kafka-it";

  /** Applies each event once; "poison" names always fail (to exercise retry and DLQ). */
  @Component
  static class MerchantListener {

    static final List<String> APPLIED = new CopyOnWriteArrayList<>();
    static final List<String> ATTEMPTS = new CopyOnWriteArrayList<>();

    private final ProcessedEvents processed;

    MerchantListener(ProcessedEvents processed) {
      this.processed = processed;
    }

    @KafkaListener(topics = TOPIC, groupId = GROUP)
    @Transactional
    void on(MerchantReference event) {
      ATTEMPTS.add(event.getMetadata().getEventId());
      if (event.getName().startsWith("poison")) {
        throw new IllegalStateException("cannot apply " + event.getName());
      }
      if (processed.firstTime(GROUP, event.getMetadata().getEventId())) {
        APPLIED.add(event.getName());
      }
    }
  }
}
