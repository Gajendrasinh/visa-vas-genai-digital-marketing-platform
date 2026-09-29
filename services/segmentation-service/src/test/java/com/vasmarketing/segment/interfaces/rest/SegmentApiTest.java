package com.vasmarketing.segment.interfaces.rest;

import static com.vasmarketing.platform.security.Permissions.SEGMENT_READ;
import static com.vasmarketing.platform.security.Permissions.SEGMENT_WRITE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vasmarketing.platform.security.PlatformRole;
import com.vasmarketing.platform.security.RolePermissions;
import com.vasmarketing.platform.testsupport.ServicePostgres;
import com.vasmarketing.platform.testsupport.TestJwts;
import com.vasmarketing.platform.types.Actor;
import com.vasmarketing.platform.types.TenantId;
import com.vasmarketing.platform.types.UserId;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
class SegmentApiTest {

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final TenantId TENANT =
      new TenantId(UUID.fromString("0191f000-0000-7000-8000-000000000001"));
  private static final RequestPostProcessor ANALYST = as(TENANT, PlatformRole.DATA_ANALYST);
  private static final String TRAVELERS =
      "{\"all\":[{\"feature\":\"travel_txn_count_90d\",\"op\":\"gte\",\"value\":3},"
          + "{\"feature\":\"marketing_consent\",\"op\":\"eq\",\"value\":true}]}";

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    ServicePostgres.register(registry, "segment");
  }

  @MockitoBean private JwtDecoder jwtDecoder;
  @Autowired private MockMvc mvc;

  private static RequestPostProcessor as(TenantId tenant, PlatformRole role) {
    return TestJwts.as(new Actor(new UserId("user-" + role.name().toLowerCase()), tenant), role);
  }

  private static MockHttpServletRequestBuilder create(String name, String definition) {
    return post("/api/v1/segments")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"name\":\"" + name + "\",\"definition\":" + definition + "}")
        .header("Idempotency-Key", UUID.randomUUID().toString());
  }

  private JsonNode created() throws Exception {
    return JSON.readTree(
        mvc.perform(create("Travelers " + UUID.randomUUID(), TRAVELERS).with(ANALYST))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  @Test
  void createsSegmentAndReturnsCanonicalDefinition() throws Exception {
    JsonNode segment = created();

    assertThat(segment.get("status").asString()).isEqualTo("DRAFT");
    assertThat(segment.get("definition")).isEqualTo(JSON.readTree(TRAVELERS));
  }

  @Test
  void rejectsDefinitionsOutsideTheAllowList() throws Exception {
    mvc.perform(
            create("Bad", "{\"feature\":\"credit_score\",\"op\":\"gt\",\"value\":700}")
                .with(ANALYST))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("SEGMENT_INVALID_DEFINITION"))
        .andExpect(jsonPath("$.detail").value("unknown feature credit_score"));
  }

  @Test
  void definitionIsFrozenAfterActivationButRenameStillWorks() throws Exception {
    String id = created().get("id").asString();
    mvc.perform(post("/api/v1/segments/" + id + "/activate").with(ANALYST))
        .andExpect(status().isOk());
    String stricter = "{\"feature\":\"travel_txn_count_90d\",\"op\":\"gte\",\"value\":6}";

    mvc.perform(
            put("/api/v1/segments/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .header("If-Match", "\"1\"")
                .content(
                    "{\"name\":\"Renamed "
                        + UUID.randomUUID()
                        + "\",\"definition\":"
                        + stricter
                        + "}")
                .with(ANALYST))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errorCode").value("SEGMENT_NOT_EDITABLE"));
    mvc.perform(
            put("/api/v1/segments/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .header("If-Match", "\"1\"")
                .content(
                    "{\"name\":\"Renamed "
                        + UUID.randomUUID()
                        + "\",\"definition\":"
                        + TRAVELERS
                        + "}")
                .with(ANALYST))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.definitionVersion").value(1));
  }

  @Test
  void otherTenantsCannotSeeSegments() throws Exception {
    String id = created().get("id").asString();

    mvc.perform(
            get("/api/v1/segments/" + id)
                .with(as(new TenantId(UUID.randomUUID()), PlatformRole.DATA_ANALYST)))
        .andExpect(status().isNotFound());
  }

  @ParameterizedTest
  @EnumSource(PlatformRole.class)
  void segmentPermissionsFollowTheMatrix(PlatformRole role) throws Exception {
    RequestPostProcessor user = as(TENANT, role);
    int createStatus =
        mvc.perform(create("Role " + UUID.randomUUID(), TRAVELERS).with(user))
            .andReturn()
            .getResponse()
            .getStatus();
    int listStatus =
        mvc.perform(get("/api/v1/segments").with(user)).andReturn().getResponse().getStatus();

    assertThat(createStatus == 403).isEqualTo(!RolePermissions.of(role).contains(SEGMENT_WRITE));
    assertThat(listStatus == 403).isEqualTo(!RolePermissions.of(role).contains(SEGMENT_READ));
  }
}
