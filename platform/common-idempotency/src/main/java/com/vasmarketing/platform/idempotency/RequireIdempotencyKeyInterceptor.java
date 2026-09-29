package com.vasmarketing.platform.idempotency;

import com.vasmarketing.platform.web.error.PlatformException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/** Rejects calls to {@link Idempotent} endpoints that omit the Idempotency-Key header. */
final class RequireIdempotencyKeyInterceptor implements HandlerInterceptor {

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    if (handler instanceof HandlerMethod method
        && method.hasMethodAnnotation(Idempotent.class)
        && request.getHeader(IdempotencyFilter.HEADER) == null) {
      throw new KeyRequired();
    }
    return true;
  }

  static final class KeyRequired extends PlatformException {
    private static final long serialVersionUID = 1L;

    KeyRequired() {
      super(
          HttpStatus.BAD_REQUEST,
          "IDEMPOTENCY_KEY_REQUIRED",
          "Idempotency-Key required",
          "This operation requires an Idempotency-Key header (e.g. a UUID) so it can be retried safely.");
    }
  }
}
