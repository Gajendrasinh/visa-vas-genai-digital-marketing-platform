package com.vasmarketing.platform.web;

import com.vasmarketing.platform.web.correlation.CorrelationIdFilter;
import com.vasmarketing.platform.web.error.ErrorStatusMapping;
import com.vasmarketing.platform.web.error.ProblemDetailsExceptionHandler;
import com.vasmarketing.platform.web.error.ProblemFactory;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;

/** Registers correlation-ID propagation and problem-details error handling in servlet services. */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(WebPlatformProperties.class)
public class CommonWebAutoConfiguration {

  @Bean
  FilterRegistrationBean<CorrelationIdFilter> correlationIdFilter() {
    FilterRegistrationBean<CorrelationIdFilter> registration =
        new FilterRegistrationBean<>(new CorrelationIdFilter());
    // Runs before security and everything else so that every log line carries the ID.
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
    return registration;
  }

  /** Status of error codes raised by platform types (pagination) in every service. */
  @Bean
  ErrorStatusMapping platformErrorStatuses() {
    return () ->
        Map.of(
            "INVALID_CURSOR", HttpStatus.BAD_REQUEST, "INVALID_PAGE_SIZE", HttpStatus.BAD_REQUEST);
  }

  @Bean
  @ConditionalOnMissingBean
  ProblemFactory problemFactory(WebPlatformProperties properties) {
    return new ProblemFactory(properties.problemTypeBase());
  }

  @Bean
  @ConditionalOnMissingBean
  ProblemDetailsExceptionHandler problemDetailsExceptionHandler(
      ProblemFactory problems, ObjectProvider<ErrorStatusMapping> mappings) {
    Map<String, HttpStatus> statuses = new HashMap<>();
    mappings.orderedStream().forEach(mapping -> statuses.putAll(mapping.statuses()));
    return new ProblemDetailsExceptionHandler(problems, statuses);
  }
}
