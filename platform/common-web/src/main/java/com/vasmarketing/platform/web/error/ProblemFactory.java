package com.vasmarketing.platform.web.error;

import com.vasmarketing.platform.web.correlation.CorrelationId;
import java.net.URI;
import java.util.Locale;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

/**
 * Builds RFC 9457 problem details with the platform's conventions: a {@code type} URI derived from
 * the error code, a stable {@code errorCode} property and the request's correlation ID.
 */
public final class ProblemFactory {

  private final URI typeBase;

  public ProblemFactory(URI typeBase) {
    this.typeBase = typeBase;
  }

  public ProblemDetail create(
      HttpStatusCode status, String errorCode, String title, String detail) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setType(typeFor(errorCode));
    problem.setTitle(title);
    problem.setProperty("errorCode", errorCode);
    addCorrelationId(problem);
    return problem;
  }

  /** Adds the correlation ID to a problem built elsewhere (e.g. by Spring's own handlers). */
  public static void addCorrelationId(ProblemDetail problem) {
    CorrelationId.current().ifPresent(id -> problem.setProperty("correlationId", id));
  }

  private URI typeFor(String errorCode) {
    String slug = errorCode.toLowerCase(Locale.ROOT).replace('_', '-');
    String base = typeBase.toString();
    return URI.create(base.endsWith("/") ? base + slug : base + "/" + slug);
  }
}
