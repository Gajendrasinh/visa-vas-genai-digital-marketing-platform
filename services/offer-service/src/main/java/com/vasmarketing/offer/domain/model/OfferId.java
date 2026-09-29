package com.vasmarketing.offer.domain.model;

import com.vasmarketing.platform.types.UuidV7;
import java.util.Objects;
import java.util.UUID;

public record OfferId(UUID value) {

  public OfferId {
    Objects.requireNonNull(value, "offer id");
  }

  public static OfferId newId() {
    return new OfferId(UuidV7.generate());
  }

  @Override
  public String toString() {
    return value.toString();
  }
}
