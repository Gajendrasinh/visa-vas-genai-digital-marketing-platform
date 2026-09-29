package com.vasmarketing.platform.types;

import java.util.Objects;

/** Identity-provider subject of a human user or service account. */
public record UserId(String value) {

  private static final int MAX_LENGTH = 64;

  public UserId {
    Objects.requireNonNull(value, "user id");
    if (value.isBlank() || value.length() > MAX_LENGTH) {
      throw new IllegalArgumentException("user id must be 1-" + MAX_LENGTH + " characters");
    }
  }

  @Override
  public String toString() {
    return value;
  }
}
