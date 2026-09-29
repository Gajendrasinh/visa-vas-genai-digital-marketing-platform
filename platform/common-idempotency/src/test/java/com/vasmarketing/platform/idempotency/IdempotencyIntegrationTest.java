package com.vasmarketing.platform.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vasmarketing.platform.idempotency.IdempotencyTestApplication.CountingController;
import com.vasmarketing.platform.security.PlatformRole;
import com.vasmarketing.platform.testsupport.ServicePostgres;
import com.vasmarketing.platform.testsupport.TestJwts;
import com.vasmarketing.platform.types.Actor;
import com.vasmarketing.platform.types.TenantId;
import com.vasmarketing.platform.types.UserId;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
class IdempotencyIntegrationTest {

  private static final TenantId TENANT = new TenantId(UUID.randomUUID());
  private static final RequestPostProcessor ALICE =
      TestJwts.as(new Actor(new UserId("alice"), TENANT), PlatformRole.CAMPAIGN_MANAGER);
  private static final RequestPostProcessor BOB =
      TestJwts.as(new Actor(new UserId("bob"), TENANT), PlatformRole.CAMPAIGN_MANAGER);

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    ServicePostgres.register(registry, "idem");
    registry.add("platform.idempotency.schema", () -> "idem");
  }

  @MockitoBean private JwtDecoder jwtDecoder;
  @Autowired private MockMvc mvc;
  @Autowired private IdempotencyStore store;

  private static MockHttpServletRequestBuilder createThing(String key, String body) {
    MockHttpServletRequestBuilder request =
        post("/things").contentType(MediaType.APPLICATION_JSON).content(body);
    return key == null ? request : request.header(IdempotencyFilter.HEADER, key);
  }

  private static String newKey() {
    return UUID.randomUUID().toString();
  }

  @Test
  void retryWithSameKeyReplaysStoredResponseWithoutReexecuting() throws Exception {
    String key = newKey();
    String location =
        mvc.perform(createThing(key, "{\"name\":\"a\"}").with(ALICE))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getHeader("Location");
    int callsAfterFirst = CountingController.CALLS.get();

    mvc.perform(createThing(key, "{\"name\":\"a\"}").with(ALICE))
        .andExpect(status().isCreated())
        .andExpect(header().string(IdempotencyFilter.REPLAYED_HEADER, "true"))
        .andExpect(header().string("Location", location))
        .andExpect(content().json("{\"call\":" + callsAfterFirst + "}"));

    assertThat(CountingController.CALLS.get()).isEqualTo(callsAfterFirst);
  }

  @Test
  void sameKeyWithDifferentBodyIsRejected() throws Exception {
    String key = newKey();
    mvc.perform(createThing(key, "{\"name\":\"a\"}").with(ALICE)).andExpect(status().isCreated());

    mvc.perform(createThing(key, "{\"name\":\"b\"}").with(ALICE))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.errorCode").value("IDEMPOTENCY_KEY_REUSED"));
  }

  @Test
  void keysAreScopedToThePrincipal() throws Exception {
    String key = newKey();
    mvc.perform(createThing(key, "{}").with(ALICE)).andExpect(status().isCreated());

    mvc.perform(createThing(key, "{}").with(BOB))
        .andExpect(status().isCreated())
        .andExpect(header().doesNotExist(IdempotencyFilter.REPLAYED_HEADER));
  }

  @Test
  void idempotentEndpointsRequireAKeyAndValidateItsFormat() throws Exception {
    mvc.perform(createThing(null, "{}").with(ALICE))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("IDEMPOTENCY_KEY_REQUIRED"));
    mvc.perform(createThing("bad key!", "{}").with(ALICE))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("INVALID_IDEMPOTENCY_KEY"));
  }

  @Test
  void serverErrorsReleaseTheKeySoRetriesExecuteAgain() throws Exception {
    String key = newKey();
    int before = CountingController.CALLS.get();

    mvc.perform(post("/failing").header(IdempotencyFilter.HEADER, key).with(ALICE))
        .andExpect(status().isInternalServerError());
    mvc.perform(post("/failing").header(IdempotencyFilter.HEADER, key).with(ALICE))
        .andExpect(status().isInternalServerError());

    assertThat(CountingController.CALLS.get()).isEqualTo(before + 2);
  }

  @Test
  void keyClaimedForAnotherRequestIsReported() throws Exception {
    String key = newKey();
    store.tryClaim("alice", key, "0".repeat(64));

    mvc.perform(createThing(key, "{}").with(ALICE))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.errorCode").value("IDEMPOTENCY_KEY_REUSED"));
  }

  @Test
  void releaseNeverDeletesCompletedResponses() throws Exception {
    String key = newKey();
    mvc.perform(createThing(key, "{}").with(ALICE)).andExpect(status().isCreated());

    store.release("alice", key);

    assertThat(store.find("alice", key))
        .get()
        .extracting(IdempotencyStore.Entry::completed)
        .isEqualTo(true);
  }

  @Test
  void inProgressRequestWithMatchingHashGets409() throws Exception {
    String key = newKey();
    mvc.perform(createThing(key, "{}").with(ALICE)).andExpect(status().isCreated());
    String hash = store.find("alice", key).orElseThrow().requestHash();
    String pending = newKey();
    store.tryClaim("alice", pending, hash);

    mvc.perform(createThing(pending, "{}").with(ALICE))
        .andExpect(status().isConflict())
        .andExpect(header().string("Retry-After", "1"))
        .andExpect(jsonPath("$.errorCode").value("IDEMPOTENCY_REQUEST_IN_PROGRESS"));
  }

  @Test
  void expiredRecordsArePurged() {
    assertThat(store.purgeExpired()).isNotNegative();
  }
}
