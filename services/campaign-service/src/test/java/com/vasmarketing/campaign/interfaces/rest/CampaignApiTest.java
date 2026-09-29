package com.vasmarketing.campaign.interfaces.rest;

import static com.vasmarketing.campaign.CampaignFixtures.TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vasmarketing.platform.security.PlatformRole;
import com.vasmarketing.platform.testsupport.ServicePostgres;
import com.vasmarketing.platform.testsupport.TestJwts;
import com.vasmarketing.platform.types.Actor;
import com.vasmarketing.platform.types.TenantId;
import com.vasmarketing.platform.types.UserId;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** HTTP contract of the campaign API against a real database. */
@SpringBootTest
@AutoConfigureMockMvc
class CampaignApiTest {

  static final RequestPostProcessor MANAGER =
      TestJwts.as(new Actor(new UserId("manager"), TENANT), PlatformRole.CAMPAIGN_MANAGER);
  static final RequestPostProcessor APPROVER =
      TestJwts.as(new Actor(new UserId("approver"), TENANT), PlatformRole.CAMPAIGN_MANAGER);
  static final RequestPostProcessor VIEWER =
      TestJwts.as(new Actor(new UserId("viewer"), TENANT), PlatformRole.READ_ONLY);
  static final RequestPostProcessor OTHER_TENANT =
      TestJwts.as(
          new Actor(new UserId("manager"), new TenantId(UUID.randomUUID())),
          PlatformRole.CAMPAIGN_MANAGER);

  private static final JsonMapper JSON = JsonMapper.builder().build();

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    ServicePostgres.register(registry, "campaign");
  }

  @MockitoBean private JwtDecoder jwtDecoder;
  @Autowired private MockMvc mvc;

  static String campaignJson(String name) {
    Instant start = Instant.now().plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
    return """
        {"name":"%s","objective":"Grow travel spend","segmentId":"%s","offerId":"%s",
         "startAt":"%s","endAt":"%s","budget":{"amount":"25000.00","currency":"USD"},"channels":["EMAIL"]}
        """
        .formatted(
            name, UUID.randomUUID(), UUID.randomUUID(), start, start.plus(30, ChronoUnit.DAYS));
  }

  static final String EMAIL_VARIANT =
      """
      {"channel":"EMAIL","variantKey":"A","language":"en","subject":"{{offer.value}} back",
       "bodyTemplate":"Book with {{merchant.name}} and get {{offer.value}} back.","generatedBy":"HUMAN"}
      """;

  private static MockHttpServletRequestBuilder postJson(String path, String body) {
    return post(path)
        .contentType(MediaType.APPLICATION_JSON)
        .content(body)
        .header("Idempotency-Key", UUID.randomUUID().toString());
  }

  private JsonNode created(RequestPostProcessor who) throws Exception {
    MvcResult result =
        mvc.perform(
                postJson("/api/v1/campaigns", campaignJson("Travel " + UUID.randomUUID()))
                    .with(who))
            .andExpect(status().isCreated())
            .andReturn();
    return JSON.readTree(result.getResponse().getContentAsString());
  }

  private JsonNode perform(MockHttpServletRequestBuilder request) throws Exception {
    return JSON.readTree(
        mvc.perform(request)
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  @Test
  void createReturnsLocationEtagAndDraft() throws Exception {
    mvc.perform(
            postJson("/api/v1/campaigns", campaignJson("Created " + UUID.randomUUID()))
                .with(MANAGER))
        .andExpect(status().isCreated())
        .andExpect(
            header().string("Location", org.hamcrest.Matchers.startsWith("/api/v1/campaigns/")))
        .andExpect(header().string("ETag", "\"0\""))
        .andExpect(jsonPath("$.status").value("DRAFT"))
        .andExpect(jsonPath("$.createdBy").value("manager"))
        .andExpect(jsonPath("$.budget.amount").value("25000.0000"));
  }

  @Test
  void endToEndApprovalFlowWithFourEyes() throws Exception {
    String id = created(MANAGER).get("id").asString();
    String base = "/api/v1/campaigns/" + id;
    String variantId =
        perform(postJson(base + "/variants", EMAIL_VARIANT).with(MANAGER))
            .get("variantId")
            .asString();
    perform(
        postJson(
                base + "/variants/" + variantId + "/compliance-review",
                "{\"passed\":true,\"report\":\"rule pack v1: pass\"}")
            .with(MANAGER));
    perform(post(base + "/submit").with(MANAGER));

    mvc.perform(postJson(base + "/approvals", "{\"decision\":\"APPROVE\"}").with(MANAGER))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.errorCode").value("CAMPAIGN_SELF_APPROVAL"));

    perform(
        postJson(base + "/approvals", "{\"decision\":\"APPROVE\",\"comment\":\"ok\"}")
            .with(APPROVER));
    JsonNode published = perform(postJson(base + "/publish", "").with(APPROVER));

    assertThat(published.get("status").asString()).isEqualTo("SCHEDULED");
    assertThat(published.get("variants").get(0).get("status").asString()).isEqualTo("APPROVED");
    assertThat(published.get("approvals").get(0).get("decidedBy").asString()).isEqualTo("approver");
  }

  @Test
  void updatesRequireACurrentEtag() throws Exception {
    String id = created(MANAGER).get("id").asString();
    String path = "/api/v1/campaigns/" + id;
    String body = campaignJson("Renamed " + UUID.randomUUID());

    mvc.perform(put(path).contentType(MediaType.APPLICATION_JSON).content(body).with(MANAGER))
        .andExpect(status().isPreconditionRequired())
        .andExpect(jsonPath("$.errorCode").value("PRECONDITION_REQUIRED"));
    mvc.perform(
            put(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("If-Match", "\"7\"")
                .with(MANAGER))
        .andExpect(status().isPreconditionFailed());
    mvc.perform(
            put(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("If-Match", "\"0\"")
                .with(MANAGER))
        .andExpect(status().isOk())
        .andExpect(header().string("ETag", "\"1\""));
  }

  @Test
  void invalidTransitionIsConflictWithStableCode() throws Exception {
    String id = created(MANAGER).get("id").asString();

    mvc.perform(postJson("/api/v1/campaigns/" + id + "/publish", "").with(MANAGER))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errorCode").value("CAMPAIGN_INVALID_TRANSITION"));
    mvc.perform(post("/api/v1/campaigns/" + id + "/submit").with(MANAGER))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errorCode").value("CAMPAIGN_MISSING_APPROVABLE_VARIANT"));
  }

  @Test
  void validationErrorsListEachField() throws Exception {
    mvc.perform(
            postJson(
                    "/api/v1/campaigns",
                    "{\"name\":\"\",\"budget\":{\"amount\":\"1e9\",\"currency\":\"usd\"}}")
                .with(MANAGER))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.violations[?(@.field == 'name')]").exists())
        .andExpect(jsonPath("$.violations[?(@.field == 'budget.currency')]").exists());
    mvc.perform(
            postJson(
                    "/api/v1/campaigns/" + created(MANAGER).get("id").asString() + "/variants",
                    "{\"channel\":\"EMAIL\",\"variantKey\":\"A\",\"language\":\"en\",\"subject\":\"s\","
                        + "\"bodyTemplate\":\"Your balance {{account.balance}}\",\"generatedBy\":\"HUMAN\"}")
                .with(MANAGER))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("CAMPAIGN_INVALID_VARIANT"));
  }

  @Test
  void otherTenantsSeeNotFoundAndListsAreTenantScoped() throws Exception {
    String id = created(MANAGER).get("id").asString();

    mvc.perform(get("/api/v1/campaigns/" + id).with(OTHER_TENANT)).andExpect(status().isNotFound());
    JsonNode page = perform(get("/api/v1/campaigns").with(OTHER_TENANT));
    assertThat(page.get("items").valueStream().map(n -> n.get("id").asString())).doesNotContain(id);
  }

  @Test
  void listingPaginatesNewestFirst() throws Exception {
    String first = created(MANAGER).get("id").asString();
    String second = created(MANAGER).get("id").asString();

    JsonNode page1 = perform(get("/api/v1/campaigns").param("limit", "1").with(VIEWER));
    assertThat(page1.get("items").get(0).get("id").asString()).isEqualTo(second);
    JsonNode page2 =
        perform(
            get("/api/v1/campaigns")
                .param("limit", "1")
                .param("cursor", page1.get("nextCursor").asString())
                .with(VIEWER));
    assertThat(page2.get("items").get(0).get("id").asString()).isEqualTo(first);

    mvc.perform(get("/api/v1/campaigns").param("limit", "500").with(VIEWER))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("INVALID_PAGE_SIZE"));
    mvc.perform(get("/api/v1/campaigns").param("cursor", "garbage").with(VIEWER))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("INVALID_CURSOR"));
  }

  @Test
  void createIsIdempotent() throws Exception {
    String key = UUID.randomUUID().toString();
    String body = campaignJson("Idempotent " + UUID.randomUUID());
    MockHttpServletRequestBuilder request =
        post("/api/v1/campaigns")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body)
            .header("Idempotency-Key", key);

    String first =
        mvc.perform(request.with(MANAGER))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String replay =
        mvc.perform(request.with(MANAGER))
            .andExpect(status().isCreated())
            .andExpect(header().string("Idempotent-Replayed", "true"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(JSON.readTree(replay).get("id")).isEqualTo(JSON.readTree(first).get("id"));
  }

  @Test
  void requestsWithoutTokenAreRejected() throws Exception {
    mvc.perform(get("/api/v1/campaigns")).andExpect(status().isUnauthorized());
  }

  @Test
  void openApiDocumentIsPublishedWithBearerScheme() throws Exception {
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.components.securitySchemes.bearer-jwt.scheme").value("bearer"))
        .andExpect(jsonPath("$.paths['/api/v1/campaigns/{id}/publish'].post").exists());
  }
}
