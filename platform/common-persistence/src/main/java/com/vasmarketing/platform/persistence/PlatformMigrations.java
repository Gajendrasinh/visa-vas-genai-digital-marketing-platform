package com.vasmarketing.platform.persistence;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.InitializingBean;

/**
 * Applies platform-owned tables from {@code classpath:db/platform} in every platform jar, with a
 * separate history table. Platform versions (V1 idempotency, V2 outbox, ...) are therefore
 * independent of each service's own migration versions.
 */
public final class PlatformMigrations implements InitializingBean {

  private final DataSource dataSource;
  private final String schema;

  public PlatformMigrations(DataSource dataSource, String schema) {
    this.dataSource = dataSource;
    this.schema = schema;
  }

  @Override
  public void afterPropertiesSet() {
    Flyway.configure()
        .dataSource(dataSource)
        .schemas(schema)
        .defaultSchema(schema)
        .createSchemas(false)
        .table("platform_schema_history")
        .locations("classpath:db/platform")
        .baselineOnMigrate(true)
        .baselineVersion("0")
        .load()
        .migrate();
  }
}
