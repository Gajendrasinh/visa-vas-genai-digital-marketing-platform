package com.vasmarketing.platform.kafka;

import java.nio.charset.StandardCharsets;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.MDC;
import org.springframework.kafka.listener.RecordInterceptor;

/** Restores the producer's correlation ID into the logging context while a record is handled. */
public final class CorrelationRecordInterceptor implements RecordInterceptor<Object, Object> {

  static final String MDC_KEY = "correlationId";

  @Override
  public ConsumerRecord<Object, Object> intercept(
      ConsumerRecord<Object, Object> record, Consumer<Object, Object> consumer) {
    Header header = record.headers().lastHeader(Headers.CORRELATION_ID);
    if (header != null) {
      MDC.put(MDC_KEY, new String(header.value(), StandardCharsets.UTF_8));
    }
    return record;
  }

  @Override
  public void afterRecord(
      ConsumerRecord<Object, Object> record, Consumer<Object, Object> consumer) {
    MDC.remove(MDC_KEY);
  }
}
