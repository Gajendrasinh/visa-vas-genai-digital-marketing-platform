package com.vasmarketing.platform.security;

import com.vasmarketing.platform.web.CommonWebAutoConfiguration;
import com.vasmarketing.platform.web.error.ProblemFactory;
import java.util.List;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import tools.jackson.databind.json.JsonMapper;

/**
 * Stateless JWT resource-server security for every Java service. JWT validation (issuer, signature,
 * expiry, audience) is configured with the standard {@code
 * spring.security.oauth2.resourceserver.jwt.*} properties.
 */
@AutoConfiguration(after = CommonWebAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(PlatformSecurityProperties.class)
@EnableMethodSecurity
public class CommonSecurityAutoConfiguration {

  @Bean
  SecurityProblemHandlers securityProblemHandlers(ProblemFactory problems, JsonMapper json) {
    return new SecurityProblemHandlers(problems, json);
  }

  @Bean
  @ConditionalOnMissingBean(SecurityFilterChain.class)
  SecurityFilterChain platformSecurityFilterChain(
      HttpSecurity http, PlatformSecurityProperties properties, SecurityProblemHandlers handlers)
      throws Exception {
    String[] publicPaths = properties.publicPaths().toArray(String[]::new);
    return http.csrf(csrf -> csrf.disable()) // bearer tokens only, no cookies: CSRF does not apply
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth -> auth.requestMatchers(publicPaths).permitAll().anyRequest().authenticated())
        .oauth2ResourceServer(
            oauth ->
                oauth
                    .jwt(
                        jwt ->
                            jwt.jwtAuthenticationConverter(
                                new PlatformJwtAuthenticationConverter(properties.tenantClaim())))
                    .authenticationEntryPoint(handlers.entryPoint())
                    .accessDeniedHandler(handlers.accessDeniedHandler()))
        .exceptionHandling(
            ex ->
                ex.authenticationEntryPoint(handlers.entryPoint())
                    .accessDeniedHandler(handlers.accessDeniedHandler()))
        .build();
  }

  @Bean
  WebMvcConfigurer actorResolverConfigurer(PlatformSecurityProperties properties) {
    return new WebMvcConfigurer() {
      @Override
      public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new ActorArgumentResolver(properties.tenantClaim()));
      }
    };
  }
}
