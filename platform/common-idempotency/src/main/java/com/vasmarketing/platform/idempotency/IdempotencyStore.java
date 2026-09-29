package com.vasmarketing.platform.idempotency;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Idempotency records in the service's own database. Each statement runs in its own transaction
 * (auto-commit), independent of the business transaction of the request.
 */
public final class IdempotencyStore {

  /** A stored record. */
  public record Entry(
      String requestHash,
      boolean completed,
      Integer responseStatus,
      String contentType,
      String location,
      byte[] body) {

    public Entry {
      body = body == null ? null : body.clone();
    }

    @Override
    public byte[] body() {
      return body == null ? null : body.clone();
    }
  }

  private final JdbcTemplate jdbc;
  private final Clock clock;
  private final Duration retention;

  public IdempotencyStore(JdbcTemplate jdbc, Clock clock, Duration retention) {
    this.jdbc = jdbc;
    this.clock = clock;
    this.retention = retention;
  }

  /** Claims the key for a new request; false if a live record already exists. */
  public boolean tryClaim(String principal, String key, String requestHash) {
    Timestamp now = Timestamp.from(clock.instant());
    jdbc.update(
        "delete from idempotency_keys where principal = ? and idem_key = ? and expires_at < ?",
        principal,
        key,
        now);
    return jdbc.update(
            "insert into idempotency_keys (principal, idem_key, request_hash, status, created_at, expires_at)"
                + " values (?, ?, ?, 'IN_PROGRESS', ?, ?) on conflict do nothing",
            principal,
            key,
            requestHash,
            now,
            Timestamp.from(clock.instant().plus(retention)))
        == 1;
  }

  public Optional<Entry> find(String principal, String key) {
    return jdbc
        .query(
            "select request_hash, status, response_status, content_type, location, response_body"
                + " from idempotency_keys where principal = ? and idem_key = ?",
            (rs, row) ->
                new Entry(
                    rs.getString("request_hash"),
                    "COMPLETED".equals(rs.getString("status")),
                    (Integer) rs.getObject("response_status"),
                    rs.getString("content_type"),
                    rs.getString("location"),
                    rs.getBytes("response_body")),
            principal,
            key)
        .stream()
        .findFirst();
  }

  public void complete(
      String principal, String key, int status, String contentType, String location, byte[] body) {
    jdbc.update(
        "update idempotency_keys set status = 'COMPLETED', response_status = ?, content_type = ?,"
            + " location = ?, response_body = ? where principal = ? and idem_key = ?",
        status,
        contentType,
        location,
        body,
        principal,
        key);
  }

  /** Frees the key after a server-side failure so the client can retry. */
  public void release(String principal, String key) {
    jdbc.update(
        "delete from idempotency_keys where principal = ? and idem_key = ? and status = 'IN_PROGRESS'",
        principal,
        key);
  }

  public int purgeExpired() {
    return jdbc.update(
        "delete from idempotency_keys where expires_at < ?", Timestamp.from(clock.instant()));
  }
}
