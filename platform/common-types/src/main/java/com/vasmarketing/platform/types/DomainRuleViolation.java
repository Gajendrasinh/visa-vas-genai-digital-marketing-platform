package com.vasmarketing.platform.types;

import java.util.Objects;

/**
 * A business rule rejected an operation. The code is stable and machine-readable; the interfaces
 * layer maps codes to HTTP statuses, so domain code stays free of transport concerns.
 */
public class DomainRuleViolation extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final String code;

  public DomainRuleViolation(String code, String message) {
    this.code = Objects.requireNonNull(code, "code");
    super(message);
  }

  public String code() {
    return code;
  }
}
