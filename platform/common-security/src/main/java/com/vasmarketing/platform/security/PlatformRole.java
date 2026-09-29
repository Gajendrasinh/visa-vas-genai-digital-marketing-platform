package com.vasmarketing.platform.security;

import java.util.Arrays;
import java.util.Optional;

/** Realm roles issued by the identity provider (docs/security.md §2). */
public enum PlatformRole {
  MARKETING_ADMIN,
  CAMPAIGN_MANAGER,
  MARKETING_ANALYST,
  DATA_ANALYST,
  AI_OPERATOR,
  READ_ONLY;

  public static Optional<PlatformRole> fromName(String name) {
    return Arrays.stream(values()).filter(r -> r.name().equals(name)).findFirst();
  }
}
