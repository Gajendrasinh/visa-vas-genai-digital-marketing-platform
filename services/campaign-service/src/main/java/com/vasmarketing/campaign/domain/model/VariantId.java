package com.vasmarketing.campaign.domain.model;

import com.vasmarketing.platform.types.UuidV7;
import java.util.Objects;
import java.util.UUID;

public record VariantId(UUID value) {

  public VariantId {
    Objects.requireNonNull(value, "variant id");
  }

  public static VariantId newId() {
    return new VariantId(UuidV7.generate());
  }
}
