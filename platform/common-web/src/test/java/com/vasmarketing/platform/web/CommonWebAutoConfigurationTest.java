package com.vasmarketing.platform.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.vasmarketing.platform.web.error.ProblemDetailsExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

class CommonWebAutoConfigurationTest {

  private final WebApplicationContextRunner webRunner =
      new WebApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(CommonWebAutoConfiguration.class));

  @Test
  void registersFilterAndHandlerInServletApplications() {
    webRunner.run(
        context -> {
          assertThat(context).hasSingleBean(ProblemDetailsExceptionHandler.class);
          assertThat(context).hasSingleBean(FilterRegistrationBean.class);
        });
  }

  @Test
  void backsOffWhenServiceDefinesItsOwnHandler() {
    webRunner
        .withBean(
            ProblemDetailsExceptionHandler.class,
            () -> new ProblemDetailsExceptionHandler(java.net.URI.create("https://custom")))
        .run(
            context ->
                assertThat(context).getBean(ProblemDetailsExceptionHandler.class).isNotNull());
  }

  @Test
  void staysInactiveOutsideWebApplications() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(CommonWebAutoConfiguration.class))
        .run(context -> assertThat(context).doesNotHaveBean(ProblemDetailsExceptionHandler.class));
  }
}
