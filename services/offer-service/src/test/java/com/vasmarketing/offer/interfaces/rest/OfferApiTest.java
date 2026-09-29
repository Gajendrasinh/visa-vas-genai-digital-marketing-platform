package com.vasmarketing.offer.interfaces.rest;

import static com.vasmarketing.offer.OfferFixtures.TENANT;
import static com.vasmarketing.platform.security.Permissions.OFFER_READ;
import static com.vasmarketing.platform.security.Permissions.OFFER_WRITE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vasmarketing.platform.security.PlatformRole;
import com.vasmarketing.platform.security.RolePermissions;
import com.vasmarketing.platform.testsupport.ServicePostgres;
import com.vasmarketing.platform.testsupport.TestJwts;
import com.vasmarketing.platform.types.Actor;
import com.vasmarketing.platform.types.TenantId;
import com.vasmarketing.platform.types.UserId;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
class OfferApiTest {

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final RequestPostProcessor ADMIN = as(TENANT, PlatformRole.MARKETING_ADMIN);

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    ServicePostgres.register(registry, "offer");
  }

  @MockitoBean private JwtDecoder jwtDecoder;
  @Autowired private MockMvc mvc;

  private static RequestPostProcessor as(TenantId tenant, PlatformRole role) {
    return TestJwts.as(new Actor(new UserId("user-" + role.name().toLowerCase()), tenant), role);
  }

  private static String terms(String title) {
    Instant start = Instant.now().minus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
    return """
        {"merchantId":"%s","title":"%s","description":"10%% back on flights","category":"TRAVEL","currency":"USD",
         "reward":{"type":"CASHBACK_PERCENT","percent":"10","maxReward":"50"},"minSpend":"100","perCustomerCap":2,
         "totalBudget":"100000","startAt":"%s","endAt":"%s"}
        """
        .formatted(UUID.randomUUID(), title, start, start.plus(60, ChronoUnit.DAYS));
  }

  private static final String RULES =
      """
      [{"type":"SEGMENT_MEMBERSHIP","segmentIds":["0191f000-0000-7000-8000-00000000a001"]},
       {"type":"COUNTRY_IN","countryCodes":["US","CA"]},
       {"type":"CATEGORY_AFFINITY","category":"TRAVEL","minTransactions90d":2},
       {"type":"CONSENT_REQUIRED"}]
      """;

  private static MockHttpServletRequestBuilder create(String body) {
    return post("/api/v1/offers")
        .contentType(MediaType.APPLICATION_JSON)
        .content(body)
        .header("Idempotency-Key", UUID.randomUUID().toString());
  }

  private JsonNode createOffer() throws Exception {
    String body =
        "{\"terms\":" + terms("Travel " + UUID.randomUUID()) + ",\"rules\":" + RULES + "}";
    return JSON.readTree(
        mvc.perform(create(body).with(ADMIN))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  @Test
  void createsOfferWithRulesAndActivatesIt() throws Exception {
    JsonNode offer = createOffer();
    assertThat(offer.get("rules")).hasSize(4);
    assertThat(offer.get("reward").get("percent").asString()).isEqualTo("10.00");

    mvc.perform(
            post("/api/v1/offers/" + offer.get("id").asString() + "/activate")
                .header("If-Match", "\"0\"")
                .with(ADMIN))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACTIVE"))
        .andExpect(header().string("ETag", "\"1\""));
  }

  @Test
  void rejectsActivationWithoutConsentRule() throws Exception {
    String body =
        "{\"terms\":"
            + terms("NoConsent " + UUID.randomUUID())
            + ",\"rules\":[{\"type\":\"MIN_TRANSACTIONS_30D\",\"minimum\":3}]}";
    String id =
        JSON.readTree(
                mvc.perform(create(body).with(ADMIN))
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .get("id")
            .asString();

    mvc.perform(post("/api/v1/offers/" + id + "/activate").with(ADMIN))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errorCode").value("OFFER_CONSENT_RULE_REQUIRED"));
  }

  @Test
  void rejectsIncompleteRulesAndInvalidRewards() throws Exception {
    mvc.perform(
            create("{\"terms\":" + terms("Bad rule") + ",\"rules\":[{\"type\":\"COUNTRY_IN\"}]}")
                .with(ADMIN))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("OFFER_INVALID_RULE"));
    String badReward = terms("Bad reward").replace("\"percent\":\"10\"", "\"percent\":\"75\"");
    mvc.perform(create("{\"terms\":" + badReward + ",\"rules\":[]}").with(ADMIN))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("OFFER_INVALID_REWARD"));
  }

  @Test
  void rulesReplacementNeedsIfMatchAndBumpsVersion() throws Exception {
    String id = createOffer().get("id").asString();
    String body = "{\"rules\":[{\"type\":\"CONSENT_REQUIRED\"}]}";

    mvc.perform(
            put("/api/v1/offers/" + id + "/rules")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(ADMIN))
        .andExpect(status().isPreconditionRequired());
    mvc.perform(
            put("/api/v1/offers/" + id + "/rules")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("If-Match", "\"0\"")
                .with(ADMIN))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ruleSetVersion").value(2))
        .andExpect(jsonPath("$.rules.length()").value(1));
  }

  @Test
  void listFiltersAndTenantIsolation() throws Exception {
    String id = createOffer().get("id").asString();

    mvc.perform(
            get("/api/v1/offers").param("category", "TRAVEL").param("status", "DRAFT").with(ADMIN))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[?(@.id == '" + id + "')]").exists());
    RequestPostProcessor otherTenant =
        as(new TenantId(UUID.randomUUID()), PlatformRole.MARKETING_ADMIN);
    mvc.perform(get("/api/v1/offers/" + id).with(otherTenant)).andExpect(status().isNotFound());
  }

  @ParameterizedTest
  @EnumSource(PlatformRole.class)
  void onlyOfferWritersCanCreateAndReadersCanRead(PlatformRole role) throws Exception {
    RequestPostProcessor user = as(TENANT, role);
    int createStatus =
        mvc.perform(
                create("{\"terms\":" + terms("Role " + UUID.randomUUID()) + ",\"rules\":[]}")
                    .with(user))
            .andReturn()
            .getResponse()
            .getStatus();
    int listStatus =
        mvc.perform(get("/api/v1/offers").with(user)).andReturn().getResponse().getStatus();

    assertThat(createStatus == 403).isEqualTo(!RolePermissions.of(role).contains(OFFER_WRITE));
    assertThat(listStatus == 403).isEqualTo(!RolePermissions.of(role).contains(OFFER_READ));
  }
}
