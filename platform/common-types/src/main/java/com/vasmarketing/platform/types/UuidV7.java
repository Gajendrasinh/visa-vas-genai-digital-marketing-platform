package com.vasmarketing.platform.types;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.UUID;

/**
 * RFC 9562 version 7 UUIDs: time-ordered, so primary-key inserts stay append-mostly in B-tree
 * indexes.
 */
public final class UuidV7 {

  private static final SecureRandom RANDOM = new SecureRandom();

  private UuidV7() {}

  public static UUID generate() {
    return generate(Clock.systemUTC());
  }

  static UUID generate(Clock clock) {
    long millis = clock.millis();
    long randA = RANDOM.nextInt(1 << 12);
    long msb = (millis << 16) | (0x7L << 12) | randA;
    long lsb = (RANDOM.nextLong() & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;
    return new UUID(msb, lsb);
  }
}
