package com.vasmarketing.platform.testsupport;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import com.vasmarketing.platform.security.PlatformJwtAuthenticationConverter;
import com.vasmarketing.platform.security.PlatformRole;
import com.vasmarketing.platform.types.Actor;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * MockMvc authentication with Keycloak-shaped tokens. Authorities are computed by the production
 * converter, so role-matrix tests exercise the real role-to-permission mapping.
 */
public final class TestJwts {

  private static final PlatformJwtAuthenticationConverter CONVERTER =
      new PlatformJwtAuthenticationConverter("tenant_id");

  private TestJwts() {}

  public static RequestPostProcessor as(Actor actor, PlatformRole... roles) {
    List<String> roleNames = Arrays.stream(roles).map(Enum::name).toList();
    return jwt()
        .jwt(
            token ->
                token
                    .subject(actor.userId().value())
                    .claim("tenant_id", actor.tenantId().toString())
                    .claim("realm_access", Map.of("roles", roleNames)))
        .authorities(
            token ->
                CONVERTER.convert(token).getAuthorities().stream()
                    .map(GrantedAuthority.class::cast)
                    .toList());
  }
}
