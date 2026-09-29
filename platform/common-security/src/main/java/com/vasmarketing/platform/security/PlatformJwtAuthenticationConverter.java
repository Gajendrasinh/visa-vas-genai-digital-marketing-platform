package com.vasmarketing.platform.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Turns a Keycloak access token into an authentication whose authorities are the permissions of its
 * realm roles (plus {@code ROLE_*}). Tokens without a valid tenant claim are rejected: every
 * request must be attributable to exactly one tenant.
 */
public final class PlatformJwtAuthenticationConverter
    implements Converter<Jwt, AbstractAuthenticationToken> {

  private final String tenantClaim;

  public PlatformJwtAuthenticationConverter(String tenantClaim) {
    this.tenantClaim = tenantClaim;
  }

  @Override
  public AbstractAuthenticationToken convert(Jwt jwt) {
    requireTenant(jwt);
    List<String> roles = realmRoles(jwt);
    Collection<GrantedAuthority> authorities = new ArrayList<>();
    RolePermissions.forRoleNames(roles)
        .forEach(p -> authorities.add(new SimpleGrantedAuthority(p)));
    roles.stream()
        .filter(r -> PlatformRole.fromName(r).isPresent())
        .forEach(r -> authorities.add(new SimpleGrantedAuthority("ROLE_" + r)));
    return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
  }

  private void requireTenant(Jwt jwt) {
    String tenant = jwt.getClaimAsString(tenantClaim);
    try {
      UUID.fromString(tenant == null ? "" : tenant);
    } catch (IllegalArgumentException e) {
      throw new InvalidBearerTokenException("token has no valid " + tenantClaim + " claim");
    }
  }

  private static List<String> realmRoles(Jwt jwt) {
    Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
    if (realmAccess == null || !(realmAccess.get("roles") instanceof Collection<?> roles)) {
      return List.of();
    }
    return roles.stream().map(String::valueOf).toList();
  }
}
