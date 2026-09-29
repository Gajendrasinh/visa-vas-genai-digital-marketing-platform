package com.vasmarketing.platform.kafka;

/** Kafka header names used across the platform (docs/kafka.md §1). */
public final class Headers {

  public static final String CORRELATION_ID = "x-correlation-id";
  public static final String EVENT_ID = "x-event-id";
  public static final String EVENT_TYPE = "x-event-type";

  private Headers() {}
}
