package com.vasmarketing.customer.domain.customer;

import com.vasmarketing.platform.types.UuidV7;
import java.util.Objects;
import java.util.UUID;

/** Pseudonymous customer identifier; the only customer reference that leaves this service. */
public record CustomerId(UUID value) {

  public CustomerId {
    Objects.requireNonNull(value, "customer id");
  }

  public static CustomerId newId() {
    return new CustomerId(UuidV7.generate());
  }

  @Override
  public String toString() {
    return value.toString();
  }
}
