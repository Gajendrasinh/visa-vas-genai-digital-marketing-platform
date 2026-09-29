package com.vasmarketing.platform.web.correlation;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;

/** Correlation ID contract shared by HTTP, Kafka and logging. */
public final class CorrelationId {

  public static final String HEADER = "X-Correlation-Id";
  public static final String MDC_KEY = "correlationId";

  /**
   * Restrictive format: inbound values end up in logs and headers, so anything else is replaced.
   */
  private static final Pattern VALID_FORMAT = Pattern.compile("^[A-Za-z0-9._-]{8,64}$");

  private CorrelationId() {}

  /** Returns the candidate if it is a well-formed ID, otherwise a newly generated one. */
  public static String resolve(String candidate) {
    if (candidate != null && VALID_FORMAT.matcher(candidate).matches()) {
      return candidate;
    }
    return UUID.randomUUID().toString();
  }

  /** The correlation ID bound to the current thread, if any. */
  public static Optional<String> current() {
    return Optional.ofNullable(MDC.get(MDC_KEY));
  }
}
