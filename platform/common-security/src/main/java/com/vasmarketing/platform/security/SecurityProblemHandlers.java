package com.vasmarketing.platform.security;

import com.vasmarketing.platform.web.error.ProblemFactory;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.json.JsonMapper;

/**
 * 401/403 as problem details, both from the filter chain (missing/invalid token, URL rules) and
 * from method security inside controllers (which would otherwise reach the generic 500 handler).
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class SecurityProblemHandlers {

  static final String UNAUTHENTICATED = "UNAUTHENTICATED";
  static final String FORBIDDEN = "FORBIDDEN";

  private final ProblemFactory problems;
  private final ObjectWriter writer;

  public SecurityProblemHandlers(ProblemFactory problems, JsonMapper json) {
    this.problems = problems;
    this.writer = json.writerFor(ProblemDetail.class);
  }

  AuthenticationEntryPoint entryPoint() {
    return (request, response, ex) -> {
      response.setHeader("WWW-Authenticate", "Bearer");
      write(response, unauthenticated());
    };
  }

  AccessDeniedHandler accessDeniedHandler() {
    return (request, response, ex) -> write(response, forbidden());
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(forbidden());
  }

  @ExceptionHandler(AuthenticationException.class)
  ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException ex) {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(unauthenticated());
  }

  private ProblemDetail unauthenticated() {
    return problems.create(
        HttpStatus.UNAUTHORIZED,
        UNAUTHENTICATED,
        "Authentication required",
        "A valid bearer token is required.");
  }

  private ProblemDetail forbidden() {
    return problems.create(
        HttpStatus.FORBIDDEN,
        FORBIDDEN,
        "Forbidden",
        "You do not have permission for this operation.");
  }

  private void write(HttpServletResponse response, ProblemDetail problem) throws IOException {
    response.setStatus(problem.getStatus());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    writer.writeValue(response.getOutputStream(), problem);
  }
}
