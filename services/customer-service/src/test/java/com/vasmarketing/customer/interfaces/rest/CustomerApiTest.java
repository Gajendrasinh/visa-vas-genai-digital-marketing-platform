package com.vasmarketing.customer.interfaces.rest;

import static com.vasmarketing.platform.security.Permissions.CUSTOMER_PROFILE_READ;
import static com.vasmarketing.platform.security.Permissions.CUSTOMER_WRITE;
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
import java.security.SecureRandom;
import java.util.Base64;
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
class CustomerApiTest {

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final TenantId TENANT = new TenantId(UUID.randomUUID());
  private static final RequestPostProcessor ADMIN = as(TENANT, PlatformRole.MARKETING_ADMIN);

  @DynamicPropertySource
  static void configure(DynamicPropertyRegistry registry) {
    ServicePostgres.register(registry, "customer");
    registry.add("customer.pii.keys.1", CustomerApiTest::randomKey);
    registry.add("customer.pii.hmac-key", CustomerApiTest::randomKey);
  }

  @MockitoBean private JwtDecoder jwtDecoder;
  @Autowired private MockMvc mvc;

  private static String randomKey() {
    byte[] key = new byte[32];
    new SecureRandom().nextBytes(key);
    return Base64.getEncoder().encodeToString(key);
  }

  private static RequestPostProcessor as(TenantId tenant, PlatformRole role) {
    return TestJwts.as(new Actor(new UserId("user-" + role.name().toLowerCase()), tenant), role);
  }

  private static String customerJson(String email) {
    return """
        {"personalData":{"fullName":"Jane Doe","email":"%s","phone":"+14155550123"},
         "countryCode":"US","birthYear":1988,
         "preferences":{"emailOptIn":true,"pushOptIn":false,"smsOptIn":true,"preferredCategories":["TRAVEL"],"language":"en"}}
        """
        .formatted(email);
  }

  private static MockHttpServletRequestBuilder register(String body) {
    return post("/api/v1/customers")
        .contentType(MediaType.APPLICATION_JSON)
        .content(body)
        .header("Idempotency-Key", UUID.randomUUID().toString());
  }

  private static String email() {
    return "jane+" + UUID.randomUUID() + "@example.com";
  }

  private JsonNode registered() throws Exception {
    return JSON.readTree(
        mvc.perform(register(customerJson(email())).with(ADMIN))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  @Test
  void responsesNeverContainRawPii() throws Exception {
    String email = email();
    String created =
        mvc.perform(register(customerJson(email)).with(ADMIN))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String id = JSON.readTree(created).get("id").asString();
    String fetched =
        mvc.perform(get("/api/v1/customers/" + id).with(ADMIN))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String profile =
        mvc.perform(get("/api/v1/customers/" + id + "/profile").with(ADMIN))
            .andReturn()
            .getResponse()
            .getContentAsString();

    for (String body : new String[] {created, fetched, profile}) {
      assertThat(body).doesNotContain("Jane Doe", email, "4155550123");
    }
    assertThat(JSON.readTree(fetched).get("personalData").get("fullName").asString())
        .isEqualTo("J*** D***");
    assertThat(JSON.readTree(profile).has("personalData")).isFalse();
  }

  @Test
  void consentMakesCustomerContactableOnOptedInChannels() throws Exception {
    String id = registered().get("id").asString();
    mvc.perform(get("/api/v1/customers/" + id + "/profile").with(ADMIN))
        .andExpect(jsonPath("$.contactableChannels.length()").value(0));

    mvc.perform(
            post("/api/v1/customers/" + id + "/consent")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"granted\":true}")
                .with(ADMIN))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.marketingConsent").value(true));

    JsonNode profile =
        JSON.readTree(
            mvc.perform(get("/api/v1/customers/" + id + "/profile").with(ADMIN))
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(profile.get("contactableChannels").valueStream().map(JsonNode::asString))
        .containsExactly("EMAIL", "SMS");
  }

  @Test
  void duplicateEmailIsConflictAndPreferencesNeedIfMatch() throws Exception {
    String email = email();
    String id =
        JSON.readTree(
                mvc.perform(register(customerJson(email)).with(ADMIN))
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .get("id")
            .asString();

    mvc.perform(register(customerJson(email.toUpperCase())).with(ADMIN))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errorCode").value("CUSTOMER_DUPLICATE_EMAIL"));
    String prefs =
        "{\"emailOptIn\":false,\"pushOptIn\":true,\"smsOptIn\":false,\"preferredCategories\":[],\"language\":\"en-GB\"}";
    mvc.perform(
            put("/api/v1/customers/" + id + "/preferences")
                .contentType(MediaType.APPLICATION_JSON)
                .content(prefs)
                .with(ADMIN))
        .andExpect(status().isPreconditionRequired());
    mvc.perform(
            put("/api/v1/customers/" + id + "/preferences")
                .contentType(MediaType.APPLICATION_JSON)
                .content(prefs)
                .header("If-Match", "\"0\"")
                .with(ADMIN))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.preferences.language").value("en-GB"));
  }

  @Test
  void rejectsInvalidPersonalData() throws Exception {
    mvc.perform(register(customerJson("not-an-email")).with(ADMIN))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
  }

  @Test
  void customersAreInvisibleToOtherTenants() throws Exception {
    String id = registered().get("id").asString();

    mvc.perform(
            get("/api/v1/customers/" + id)
                .with(as(new TenantId(UUID.randomUUID()), PlatformRole.MARKETING_ADMIN)))
        .andExpect(status().isNotFound());
  }

  @Test
  void merchantsDeriveCategoryAndAreReadableByOfferReaders() throws Exception {
    String body =
        "{\"name\":\"SkyWays Air\",\"mcc\":\"4511\",\"countryCode\":\"US\",\"city\":\"Denver\"}";
    String id =
        JSON.readTree(
                mvc.perform(
                        post("/api/v1/merchants")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body)
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .with(ADMIN))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.category").value("TRAVEL"))
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .get("id")
            .asString();

    mvc.perform(get("/api/v1/merchants/" + id).with(as(TENANT, PlatformRole.READ_ONLY)))
        .andExpect(status().isOk());
    mvc.perform(
            post("/api/v1/merchants/" + id + "/deactivate")
                .with(as(TENANT, PlatformRole.CAMPAIGN_MANAGER)))
        .andExpect(status().isForbidden());
  }

  @ParameterizedTest
  @EnumSource(PlatformRole.class)
  void customerPermissionsFollowTheMatrix(PlatformRole role) throws Exception {
    RequestPostProcessor user = as(TENANT, role);
    int registerStatus =
        mvc.perform(register(customerJson(email())).with(user))
            .andReturn()
            .getResponse()
            .getStatus();
    int readStatus =
        mvc.perform(get("/api/v1/customers/" + UUID.randomUUID()).with(user))
            .andReturn()
            .getResponse()
            .getStatus();

    assertThat(registerStatus == 403).isEqualTo(!RolePermissions.of(role).contains(CUSTOMER_WRITE));
    assertThat(readStatus == 403)
        .isEqualTo(!RolePermissions.of(role).contains(CUSTOMER_PROFILE_READ));
  }
}
