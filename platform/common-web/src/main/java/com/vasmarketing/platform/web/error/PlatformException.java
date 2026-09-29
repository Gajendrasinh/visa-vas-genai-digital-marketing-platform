package com.vasmarketing.platform.web.error;

import java.util.Objects;
import org.springframework.http.HttpStatus;

/**
 * Base class for exceptions that map to an RFC 9457 problem response. Services extend it with
 * domain-specific types; the error code is a stable, machine-readable identifier.
 */
public abstract class PlatformException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final HttpStatus status;
  private final String errorCode;
  private final String title;

  protected PlatformException(HttpStatus status, String errorCode, String title, String detail) {
    // Validated before super() (JEP 513) so a failed check never leaves a partially built object.
    this.status = Objects.requireNonNull(status, "status");
    this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
    this.title = Objects.requireNonNull(title, "title");
    super(detail);
  }

  public HttpStatus status() {
    return status;
  }

  public String errorCode() {
    return errorCode;
  }

  public String title() {
    return title;
  }
}
