package com.vasmarketing.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sun.net.httpserver.HttpServer;
import com.vasmarketing.platform.security.PlatformRole;
import com.vasmarketing.platform.testsupport.TestJwts;
import com.vasmarketing.platform.types.Actor;
import com.vasmarketing.platform.types.TenantId;
import com.vasmarketing.platform.types.UserId;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Routes to a stub backend and checks edge authentication and header propagation. */
@SpringBootTest
@AutoConfigureMockMvc
class GatewayRoutingTest {

  private static final AtomicReference<String> SEEN_CORRELATION_ID = new AtomicReference<>();
  private static final AtomicReference<String> SEEN_AUTHORIZATION = new AtomicReference<>();
  private static final HttpServer BACKEND = startBackend();

  @DynamicPropertySource
  static void routes(DynamicPropertyRegistry registry) {
    String url = "http://localhost:" + BACKEND.getAddress().getPort();
    registry.add("CAMPAIGN_SERVICE_URL", () -> url);
    registry.add("CUSTOMER_SERVICE_URL", () -> url);
  }

  @AfterAll
  static void stop() {
    BACKEND.stop(0);
  }

  @MockitoBean private JwtDecoder jwtDecoder;
  @Autowired private MockMvc mvc;

  private static HttpServer startBackend() {
    try {
      HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
      server.createContext(
          "/",
          exchange -> {
            SEEN_CORRELATION_ID.set(exchange.getRequestHeaders().getFirst("X-Correlation-Id"));
            SEEN_AUTHORIZATION.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body =
                ("{\"path\":\"" + exchange.getRequestURI().getPath() + "\"}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
          });
      server.start();
      return server;
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  @Test
  void rejectsUnauthenticatedRequestsAtTheEdge() throws Exception {
    mvc.perform(get("/api/v1/campaigns"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
  }

  @Test
  void routesByPathAndPropagatesCorrelationId() throws Exception {
    Actor actor = new Actor(new UserId("manager"), new TenantId(UUID.randomUUID()));

    mvc.perform(
            get("/api/v1/merchants/42")
                .header("X-Correlation-Id", "edge-test-0001")
                .with(TestJwts.as(actor, PlatformRole.READ_ONLY)))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Correlation-Id", "edge-test-0001"))
        .andExpect(content().json("{\"path\":\"/api/v1/merchants/42\"}"));

    assertThat(SEEN_CORRELATION_ID.get()).isEqualTo("edge-test-0001");
  }

  @Test
  void unknownRoutesAreNotFound() throws Exception {
    Actor actor = new Actor(new UserId("manager"), new TenantId(UUID.randomUUID()));

    mvc.perform(get("/api/v1/unknown").with(TestJwts.as(actor, PlatformRole.READ_ONLY)))
        .andExpect(status().isNotFound());
  }
}
