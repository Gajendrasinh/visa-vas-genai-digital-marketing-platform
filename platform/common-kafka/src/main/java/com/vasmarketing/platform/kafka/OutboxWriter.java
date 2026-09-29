package com.vasmarketing.platform.kafka;

import java.util.List;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Appends events to the outbox table inside the caller's transaction, so the state change and its
 * events commit or roll back together (no database/Kafka dual write).
 */
public final class OutboxWriter {

  private final JdbcTemplate jdbc;

  public OutboxWriter(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void append(List<OutboxMessage> messages) {
    if (messages.isEmpty()) {
      return;
    }
    if (!TransactionSynchronizationManager.isActualTransactionActive()) {
      throw new IllegalStateException("outbox writes must run inside the business transaction");
    }
    String correlationId = MDC.get("correlationId");
    for (OutboxMessage message : messages) {
      jdbc.update(
          "insert into outbox_events (id, topic, message_key, event_type, payload_class, payload, correlation_id)"
              + " values (?, ?, ?, ?, ?, ?, ?)",
          message.eventId(),
          message.topic(),
          message.key(),
          message.eventType(),
          message.payload().getClass().getName(),
          AvroBinary.encode(message.payload()),
          correlationId);
    }
  }
}
