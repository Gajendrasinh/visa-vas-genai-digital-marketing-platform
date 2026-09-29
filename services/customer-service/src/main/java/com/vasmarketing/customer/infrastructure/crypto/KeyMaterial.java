package com.vasmarketing.customer.infrastructure.crypto;

import java.util.Base64;

final class KeyMaterial {

  static final int KEY_BYTES = 32;

  private KeyMaterial() {}

  /** Decodes and validates a base64 256-bit key, failing fast with an actionable message. */
  static byte[] decode(String name, String base64) {
    byte[] key;
    try {
      key = Base64.getDecoder().decode(base64 == null ? "" : base64.trim());
    } catch (IllegalArgumentException e) {
      throw invalid(name);
    }
    if (key.length != KEY_BYTES) {
      throw invalid(name);
    }
    return key;
  }

  private static IllegalStateException invalid(String name) {
    return new IllegalStateException(
        name + " must be a base64-encoded 256-bit key (generate with: openssl rand -base64 32)");
  }
}
