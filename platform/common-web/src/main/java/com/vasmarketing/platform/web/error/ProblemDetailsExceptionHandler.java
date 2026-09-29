package com.vasmarketing.platform.web.error;

import com.vasmarketing.platform.web.correlation.CorrelationId;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Maps all errors to {@code application/problem+json} with a stable {@code errorCode} and the
 * request's correlation ID. Internal exception details are never returned to clients.
 */
@RestControllerAdvice
public class ProblemDetailsExceptionHandler extends ResponseEntityExceptionHandler {

  static final String VALIDATION_FAILED = "VALIDATION_FAILED";
  static final String INTERNAL_ERROR = "INTERNAL_ERROR";

  private static final Logger log = LoggerFactory.getLogger(ProblemDetailsExceptionHandler.class);

  private final URI problemTypeBase;

  public ProblemDetailsExceptionHandler(URI problemTypeBase) {
    this.problemTypeBase = problemTypeBase;
  }

  @ExceptionHandler(PlatformException.class)
  ResponseEntity<ProblemDetail> handlePlatformException(PlatformException ex) {
    ProblemDetail problem = problem(ex.status(), ex.errorCode(), ex.title(), ex.getMessage());
    return ResponseEntity.status(ex.status()).body(problem);
  }

  @ExceptionHandler(ConstraintViolationException.class)
  ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex) {
    List<Map<String, String>> violations =
        ex.getConstraintViolations().stream()
            .map(v -> violation(v.getPropertyPath().toString(), v.getMessage()))
            .toList();
    return validationProblem(violations);
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
    log.error("Unhandled exception", ex);
    ProblemDetail problem =
        problem(
            HttpStatus.INTERNAL_SERVER_ERROR,
            INTERNAL_ERROR,
            "Internal error",
            "An unexpected error occurred. Quote the correlation ID when reporting it.");
    return ResponseEntity.internalServerError().body(problem);
  }

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    List<Map<String, String>> violations =
        ex.getBindingResult().getFieldErrors().stream()
            .map(e -> violation(e.getField(), e.getDefaultMessage()))
            .toList();
    return ResponseEntity.badRequest().body(validationProblem(violations).getBody());
  }

  /** Enriches problem bodies produced by Spring's built-in handlers (e.g. 404, 405, 415). */
  @Override
  protected ResponseEntity<Object> createResponseEntity(
      Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
    if (body instanceof ProblemDetail problem) {
      addCorrelationId(problem);
    }
    return super.createResponseEntity(body, headers, statusCode, request);
  }

  private ResponseEntity<ProblemDetail> validationProblem(List<Map<String, String>> violations) {
    ProblemDetail problem =
        problem(
            HttpStatus.BAD_REQUEST,
            VALIDATION_FAILED,
            "Request validation failed",
            "One or more fields are invalid.");
    problem.setProperty("violations", violations);
    return ResponseEntity.badRequest().body(problem);
  }

  private ProblemDetail problem(HttpStatus status, String errorCode, String title, String detail) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setType(typeFor(errorCode));
    problem.setTitle(title);
    problem.setProperty("errorCode", errorCode);
    addCorrelationId(problem);
    return problem;
  }

  private URI typeFor(String errorCode) {
    String slug = errorCode.toLowerCase(Locale.ROOT).replace('_', '-');
    String base = problemTypeBase.toString();
    return URI.create(base.endsWith("/") ? base + slug : base + "/" + slug);
  }

  private static void addCorrelationId(ProblemDetail problem) {
    CorrelationId.current().ifPresent(id -> problem.setProperty("correlationId", id));
  }

  private static Map<String, String> violation(String field, String message) {
    return Map.of("field", field, "message", message == null ? "invalid" : message);
  }
}
