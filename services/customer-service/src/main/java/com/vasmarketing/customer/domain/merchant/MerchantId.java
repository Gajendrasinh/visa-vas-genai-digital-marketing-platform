package com.vasmarketing.customer.domain.merchant;

import com.vasmarketing.platform.types.UuidV7;
import java.util.Objects;
import java.util.UUID;

public record MerchantId(UUID value) {

  public MerchantId {
    Objects.requireNonNull(value, "merchant id");
  }

  public static MerchantId newId() {
    return new MerchantId(UuidV7.generate());
  }

  @Override
  public String toString() {
    return value.toString();
  }
}
