package com.vasmarketing.platform.idempotency;

import com.vasmarketing.platform.web.CommonWebAutoConfiguration;
import com.vasmarketing.platform.web.error.ProblemFactory;
import java.time.Clock;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.http.ProblemDetail;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import tools.jackson.databind.json.JsonMapper;

/** Idempotency-Key support for servlet services that have a DataSource. */
@AutoConfiguration(after = {DataSourceAutoConfiguration.class, CommonWebAutoConfiguration.class})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnBean(DataSource.class)
@EnableConfigurationProperties(IdempotencyProperties.class)
@EnableScheduling
public class IdempotencyAutoConfiguration {

  /** After Spring Security's filter chain (order -100), so the principal is known. */
  static final int FILTER_ORDER = 0;

  @Bean
  PlatformMigrations platformMigrations(
      DataSource dataSource,
      IdempotencyProperties properties,
      @Value("${spring.flyway.default-schema:public}") String serviceSchema) {
    return new PlatformMigrations(
        dataSource, properties.schema() == null ? serviceSchema : properties.schema());
  }

  @Bean
  IdempotencyStore idempotencyStore(DataSource dataSource, IdempotencyProperties properties) {
    return new IdempotencyStore(
        new JdbcTemplate(dataSource), Clock.systemUTC(), properties.retention());
  }

  @Bean
  FilterRegistrationBean<IdempotencyFilter> idempotencyFilter(
      IdempotencyStore store,
      ProblemFactory problems,
      JsonMapper json,
      IdempotencyProperties properties) {
    FilterRegistrationBean<IdempotencyFilter> registration =
        new FilterRegistrationBean<>(
            new IdempotencyFilter(
                store, problems, json.writerFor(ProblemDetail.class), properties.maxBodyBytes()));
    registration.setOrder(FILTER_ORDER);
    return registration;
  }

  @Bean
  WebMvcConfigurer idempotencyKeyRequirement() {
    return new WebMvcConfigurer() {
      @Override
      public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new RequireIdempotencyKeyInterceptor());
      }
    };
  }

  @Bean
  ExpiredKeyPurger expiredKeyPurger(IdempotencyStore store) {
    return new ExpiredKeyPurger(store);
  }

  /** Removes expired records hourly; expiry is also enforced on claim. */
  static final class ExpiredKeyPurger {
    private final IdempotencyStore store;

    ExpiredKeyPurger(IdempotencyStore store) {
      this.store = store;
    }

    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT5M")
    void purge() {
      store.purgeExpired();
    }
  }
}
