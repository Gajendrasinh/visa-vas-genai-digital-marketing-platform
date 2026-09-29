package com.vasmarketing.platform.security;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/** Documents the bearer-token scheme and the platform's error/header conventions in OpenAPI. */
@AutoConfiguration
@ConditionalOnClass(OpenAPI.class)
public class OpenApiSecurityAutoConfiguration {

  static final String BEARER = "bearer-jwt";

  @Bean
  @ConditionalOnMissingBean
  OpenAPI platformOpenApi(@Value("${spring.application.name:service}") String serviceName) {
    return new OpenAPI()
        .info(
            new Info()
                .title(serviceName + " API")
                .version("v1")
                .description(
                    "Errors are RFC 9457 problem details with a stable errorCode. Unsafe creates and"
                        + " publish require an Idempotency-Key header; updates use ETag/If-Match."))
        .components(
            new Components()
                .addSecuritySchemes(
                    BEARER,
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description(
                            "Keycloak access token (realm vasmkt); tenant from the tenant_id claim")))
        .addSecurityItem(new SecurityRequirement().addList(BEARER));
  }
}
