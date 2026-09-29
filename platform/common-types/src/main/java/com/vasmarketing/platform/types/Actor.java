package com.vasmarketing.platform.types;

import java.util.Objects;

/** The authenticated principal on whose behalf a use case runs. */
public record Actor(UserId userId, TenantId tenantId) {

  public Actor {
    Objects.requireNonNull(userId, "userId");
    Objects.requireNonNull(tenantId, "tenantId");
  }
}
