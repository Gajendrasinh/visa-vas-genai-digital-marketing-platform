package com.vasmarketing.platform.testsupport;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * One Postgres container per test JVM, provisioned the way infrastructure/docker/postgres does it:
 * each service gets its own schema owned by a login role with no access to other schemas. Services
 * connect (and run Flyway) as that role, so missing grants fail in tests exactly as in production.
 */
public final class ServicePostgres {

  /** Random per test JVM, so no credential is ever hard-coded. */
  private static final String SERVICE_PASSWORD = UUID.randomUUID().toString();

  private static final PostgreSQLContainer CONTAINER =
      new PostgreSQLContainer("postgres:17-alpine").withDatabaseName("vasmkt");

  private static final Set<String> PROVISIONED = ConcurrentHashMap.newKeySet();

  private ServicePostgres() {}

  /** Registers datasource and Flyway properties for the given service schema. */
  public static void register(DynamicPropertyRegistry registry, String schema) {
    String role = provision(schema);
    registry.add("spring.datasource.url", CONTAINER::getJdbcUrl);
    registry.add("spring.datasource.username", () -> role);
    registry.add("spring.datasource.password", () -> SERVICE_PASSWORD);
  }

  /** Superuser connection, for tests that must bypass the service role (e.g. DB constraints). */
  public static Connection adminConnection() throws SQLException {
    start();
    return DriverManager.getConnection(
        CONTAINER.getJdbcUrl(), CONTAINER.getUsername(), CONTAINER.getPassword());
  }

  /** Connection as the service role, for tests of role-level restrictions such as RLS. */
  public static Connection serviceConnection(String schema) throws SQLException {
    return DriverManager.getConnection(CONTAINER.getJdbcUrl(), provision(schema), SERVICE_PASSWORD);
  }

  private static String provision(String schema) {
    if (!schema.matches("[a-z_]{1,30}")) {
      throw new IllegalArgumentException("invalid schema name: " + schema);
    }
    start();
    String role = "svc_" + schema;
    if (PROVISIONED.add(schema)) {
      try (Connection connection = adminConnection();
          Statement statement = connection.createStatement()) {
        statement.execute("REVOKE ALL ON SCHEMA public FROM PUBLIC");
        statement.execute("CREATE ROLE " + role + " LOGIN PASSWORD '" + SERVICE_PASSWORD + "'");
        statement.execute("CREATE SCHEMA " + schema + " AUTHORIZATION " + role);
        statement.execute("ALTER ROLE " + role + " SET search_path = " + schema);
      } catch (SQLException e) {
        throw new IllegalStateException("failed to provision schema " + schema, e);
      }
    }
    return role;
  }

  private static synchronized void start() {
    if (!CONTAINER.isRunning()) {
      CONTAINER.start();
    }
  }
}
