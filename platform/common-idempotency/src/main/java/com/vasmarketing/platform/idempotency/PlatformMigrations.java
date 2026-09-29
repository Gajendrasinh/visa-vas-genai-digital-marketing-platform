package com.vasmarketing.platform.idempotency;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.InitializingBean;

/**
 * Applies platform-owned tables (classpath:db/platform) with a separate history table, so their
 * versions are independent of each service's own migrations.
 */
final class PlatformMigrations implements InitializingBean {

  private final DataSource dataSource;
  private final String schema;

  PlatformMigrations(DataSource dataSource, String schema) {
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
