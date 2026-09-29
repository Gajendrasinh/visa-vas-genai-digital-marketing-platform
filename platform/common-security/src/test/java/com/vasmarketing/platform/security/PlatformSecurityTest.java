package com.vasmarketing.platform.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest
class PlatformSecurityTest {

  private static final String TENANT = "0191f000-0000-7000-8000-000000000001";

  @Autowired private MockMvc mvc;

  // Real tokens are decoded by Keycloak's JWKS; here the post-processor supplies the principal.
  @MockitoBean private JwtDecoder jwtDecoder;

  private static RequestPostProcessor user(String... roles) {
    PlatformJwtAuthenticationConverter converter =
        new PlatformJwtAuthenticationConverter("tenant_id");
    return jwt()
        .jwt(
            t ->
                t.subject("manager")
                    .claim("tenant_id", TENANT)
                    .claim("realm_access", Map.of("roles", List.of(roles))))
        .authorities(t -> converter.convert(t).getAuthorities());
  }

  @Test
  void missingTokenIs401ProblemDetails() throws Exception {
    mvc.perform(get("/probe/whoami"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string("WWW-Authenticate", "Bearer"))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
  }

  @Test
  void resolvesActorFromTokenOnly() throws Exception {
    mvc.perform(get("/probe/whoami").with(user("READ_ONLY")))
        .andExpect(status().isOk())
        .andExpect(content().string("manager@" + TENANT));
  }

  @Test
  void methodSecurityDeniesWithProblemDetails() throws Exception {
    mvc.perform(get("/probe/publish").with(user("READ_ONLY")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    mvc.perform(get("/probe/publish").with(user("CAMPAIGN_MANAGER"))).andExpect(status().isOk());
  }

  @Test
  void healthIsPublic() throws Exception {
    mvc.perform(get("/actuator/health/liveness"))
        .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(401));
  }

  @Test
  void converterRejectsTokensWithoutValidTenant() {
    PlatformJwtAuthenticationConverter converter =
        new PlatformJwtAuthenticationConverter("tenant_id");
    Jwt noTenant = token(Map.of("realm_access", Map.of("roles", List.of("READ_ONLY"))));
    Jwt badTenant = token(Map.of("tenant_id", "issuer-a"));

    assertThatThrownBy(() -> converter.convert(noTenant))
        .isInstanceOf(InvalidBearerTokenException.class);
    assertThatThrownBy(() -> converter.convert(badTenant))
        .isInstanceOf(InvalidBearerTokenException.class);
  }

  @Test
  void converterMapsRolesToPermissionsAndIgnoresUnknownRoles() {
    PlatformJwtAuthenticationConverter converter =
        new PlatformJwtAuthenticationConverter("tenant_id");
    Jwt jwt =
        token(
            Map.of(
                "tenant_id", UUID.randomUUID().toString(),
                "realm_access", Map.of("roles", List.of("READ_ONLY", "default-roles-vasmkt"))));

    assertThat(converter.convert(jwt).getAuthorities())
        .extracting(Object::toString)
        .contains("campaign:read", "ROLE_READ_ONLY")
        .doesNotContain("ROLE_default-roles-vasmkt", "campaign:draft");
  }

  private static Jwt token(Map<String, Object> claims) {
    Jwt.Builder builder =
        Jwt.withTokenValue("t")
            .header("alg", "RS256")
            .subject("u")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(60));
    claims.forEach(builder::claim);
    return builder.build();
  }
}
