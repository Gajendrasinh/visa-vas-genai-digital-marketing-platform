package com.vasmarketing.platform.security;

import com.vasmarketing.platform.types.Actor;
import com.vasmarketing.platform.types.TenantId;
import com.vasmarketing.platform.types.UserId;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Lets controllers declare an {@link Actor} parameter. The tenant comes only from the verified
 * token, never from the request body or path, so a caller cannot act for another tenant.
 */
public final class ActorArgumentResolver implements HandlerMethodArgumentResolver {

  private final String tenantClaim;

  public ActorArgumentResolver(String tenantClaim) {
    this.tenantClaim = tenantClaim;
  }

  @Override
  public boolean supportsParameter(MethodParameter parameter) {
    return Actor.class.equals(parameter.getParameterType());
  }

  @Override
  public Actor resolveArgument(
      MethodParameter parameter,
      ModelAndViewContainer mavContainer,
      NativeWebRequest webRequest,
      WebDataBinderFactory binderFactory) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (!(authentication instanceof JwtAuthenticationToken token)) {
      throw new AuthenticationCredentialsNotFoundException("no authenticated user");
    }
    return new Actor(
        new UserId(token.getToken().getSubject()),
        TenantId.of(token.getToken().getClaimAsString(tenantClaim)));
  }
}
