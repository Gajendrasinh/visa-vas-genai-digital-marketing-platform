package com.vasmarketing.campaign.interfaces.rest;

import static com.vasmarketing.campaign.CampaignFixtures.TENANT;
import static com.vasmarketing.platform.security.Permissions.CAMPAIGN_APPROVE;
import static com.vasmarketing.platform.security.Permissions.CAMPAIGN_DRAFT;
import static com.vasmarketing.platform.security.Permissions.CAMPAIGN_PUBLISH;
import static com.vasmarketing.platform.security.Permissions.CAMPAIGN_READ;
import static com.vasmarketing.platform.security.Permissions.CAMPAIGN_SUBMIT;
import static org.assertj.core.api.Assertions.assertThat;

import com.vasmarketing.platform.security.PlatformRole;
import com.vasmarketing.platform.security.RolePermissions;
import com.vasmarketing.platform.testsupport.ServicePostgres;
import com.vasmarketing.platform.testsupport.TestJwts;
import com.vasmarketing.platform.types.Actor;
import com.vasmarketing.platform.types.UserId;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Generated role × endpoint matrix: every role is sent to every operation with a valid request; the
 * response must be 403 exactly when the role lacks the operation's permission. Allowed calls may
 * legitimately fail later (404 for a random id, 409), but never with 403.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CampaignAuthorizationMatrixTest {

  private static final String ID = "/api/v1/campaigns/" + UUID.randomUUID();

  record Endpoint(HttpMethod method, String path, String body, String permission) {}

  static final Endpoint[] ENDPOINTS = {
    new Endpoint(HttpMethod.GET, "/api/v1/campaigns", null, CAMPAIGN_READ),
    new Endpoint(HttpMethod.GET, ID, null, CAMPAIGN_READ),
    new Endpoint(
        HttpMethod.POST,
        "/api/v1/campaigns",
        CampaignApiTest.campaignJson("Matrix"),
        CAMPAIGN_DRAFT),
    new Endpoint(HttpMethod.PUT, ID, CampaignApiTest.campaignJson("Matrix"), CAMPAIGN_DRAFT),
    new Endpoint(HttpMethod.POST, ID + "/variants", CampaignApiTest.EMAIL_VARIANT, CAMPAIGN_DRAFT),
    new Endpoint(HttpMethod.POST, ID + "/submit", null, CAMPAIGN_SUBMIT),
    new Endpoint(
        HttpMethod.POST, ID + "/approvals", "{\"decision\":\"APPROVE\"}", CAMPAIGN_APPROVE),
    new Endpoint(HttpMethod.POST, ID + "/publish", null, CAMPAIGN_PUBLISH),
    new Endpoint(HttpMethod.POST, ID + "/pause", null, CAMPAIGN_PUBLISH),
  };

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    ServicePostgres.register(registry, "campaign");
  }

  @MockitoBean private JwtDecoder jwtDecoder;
  @Autowired private MockMvc mvc;

  static Stream<Arguments> matrix() {
    return Arrays.stream(ENDPOINTS)
        .flatMap(e -> Arrays.stream(PlatformRole.values()).map(role -> Arguments.of(role, e)));
  }

  @ParameterizedTest(name = "{0} -> {1}")
  @MethodSource("matrix")
  void forbiddenExactlyWhenPermissionIsMissing(PlatformRole role, Endpoint endpoint)
      throws Exception {
    var request =
        MockMvcRequestBuilders.request(endpoint.method(), endpoint.path())
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .header("If-Match", "\"0\"")
            .with(TestJwts.as(new Actor(new UserId("matrix-user"), TENANT), role));
    if (endpoint.body() != null) {
      request.contentType(MediaType.APPLICATION_JSON).content(endpoint.body());
    }

    int status = mvc.perform(request).andReturn().getResponse().getStatus();

    boolean permitted = RolePermissions.of(role).contains(endpoint.permission());
    if (permitted) {
      assertThat(status)
          .as("%s should be allowed on %s %s", role, endpoint.method(), endpoint.path())
          .isNotEqualTo(403);
    } else {
      assertThat(status)
          .as("%s should be denied on %s %s", role, endpoint.method(), endpoint.path())
          .isEqualTo(403);
    }
  }
}
