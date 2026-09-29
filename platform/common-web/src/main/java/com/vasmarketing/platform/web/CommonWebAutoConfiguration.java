package com.vasmarketing.platform.web;

import com.vasmarketing.platform.web.correlation.CorrelationIdFilter;
import com.vasmarketing.platform.web.error.ProblemDetailsExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

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

  @Bean
  @ConditionalOnMissingBean
  ProblemDetailsExceptionHandler problemDetailsExceptionHandler(WebPlatformProperties properties) {
    return new ProblemDetailsExceptionHandler(properties.problemTypeBase());
  }
}
