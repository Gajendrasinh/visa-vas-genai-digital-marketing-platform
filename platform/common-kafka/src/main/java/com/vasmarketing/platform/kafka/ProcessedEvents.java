package com.vasmarketing.platform.kafka;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Consumer-side idempotency. Call inside the transaction that applies the event: if the event was
 * already processed by this consumer group, the caller skips it; otherwise the marker commits
 * atomically with the state change.
 */
public final class ProcessedEvents {

  private final JdbcTemplate jdbc;

  public ProcessedEvents(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** True the first time this group sees the event; false for duplicates. */
  public boolean firstTime(String consumerGroup, String eventId) {
    if (!TransactionSynchronizationManager.isActualTransactionActive()) {
      throw new IllegalStateException("dedup marker must be written in the consumer's transaction");
    }
    return jdbc.update(
            "insert into processed_events (consumer_group, event_id) values (?, ?) on conflict do nothing",
            consumerGroup,
            eventId)
        == 1;
  }
}
