package com.vasmarketing.platform.persistence;

import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Runs platform migrations in the service's schema ({@code platform.persistence.schema}, default
 * {@code spring.flyway.default-schema}).
 */
@AutoConfiguration(after = DataSourceAutoConfiguration.class)
@ConditionalOnBean(DataSource.class)
public class PlatformPersistenceAutoConfiguration {

  @Bean
  PlatformMigrations platformMigrations(
      DataSource dataSource,
      @Value("${platform.persistence.schema:${spring.flyway.default-schema:public}}")
          String schema) {
    return new PlatformMigrations(dataSource, schema);
  }
}
