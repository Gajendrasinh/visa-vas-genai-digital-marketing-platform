package com.vasmarketing.platform.types;

import java.util.Objects;
import java.util.UUID;

/** Identifies the tenant (issuer / financial institution) that owns a piece of data. */
public record TenantId(UUID value) {

  public TenantId {
    Objects.requireNonNull(value, "tenant id");
  }

  public static TenantId of(String value) {
    return new TenantId(UUID.fromString(value));
  }

  @Override
  public String toString() {
    return value.toString();
  }
}
