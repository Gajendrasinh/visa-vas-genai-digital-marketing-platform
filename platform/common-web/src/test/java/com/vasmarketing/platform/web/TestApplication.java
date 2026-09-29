package com.vasmarketing.platform.web;

import com.vasmarketing.platform.web.error.ErrorStatusMapping;
import java.util.Map;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;

/**
 * Minimal boot configuration for test slices. It deliberately does not component-scan, so the
 * library's beans are only present through its auto-configuration, as they are in services.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
public class TestApplication {

  @Bean
  ErrorStatusMapping testErrorStatuses() {
    return () -> Map.of("TEST_RULE_CONFLICT", HttpStatus.CONFLICT);
  }
}
