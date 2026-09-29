package com.vasmarketing.platform.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param tenantClaim JWT claim carrying the tenant (issuer) UUID
 * @param publicPaths paths reachable without a token (health probes, API docs)
 */
@ConfigurationProperties("platform.security")
public record PlatformSecurityProperties(
    @DefaultValue("tenant_id") String tenantClaim,
    @DefaultValue({"/actuator/health/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"})
        List<String> publicPaths) {

  public PlatformSecurityProperties {
    publicPaths = List.copyOf(publicPaths);
  }
}
