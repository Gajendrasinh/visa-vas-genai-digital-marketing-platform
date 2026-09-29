package com.vasmarketing.platform.web.error;

import com.vasmarketing.platform.types.DomainRuleViolation;
import com.vasmarketing.platform.types.Precondition;
import com.vasmarketing.platform.types.ResourceNotFound;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
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
  static final String NOT_FOUND = "RESOURCE_NOT_FOUND";
  static final String PRECONDITION_FAILED = "PRECONDITION_FAILED";
  static final String CONCURRENT_MODIFICATION = "CONCURRENT_MODIFICATION";

  private static final Logger log = LoggerFactory.getLogger(ProblemDetailsExceptionHandler.class);
  private static final HttpStatus UNPROCESSABLE = HttpStatus.valueOf(422);

  private final ProblemFactory problems;
  private final Map<String, HttpStatus> codeStatuses;

  public ProblemDetailsExceptionHandler(
      ProblemFactory problems, Map<String, HttpStatus> codeStatuses) {
    this.problems = problems;
    this.codeStatuses = Map.copyOf(codeStatuses);
  }

  @ExceptionHandler(PlatformException.class)
  ResponseEntity<ProblemDetail> handlePlatformException(PlatformException ex) {
    return respond(ex.status(), ex.errorCode(), ex.title(), ex.getMessage());
  }

  @ExceptionHandler(DomainRuleViolation.class)
  ResponseEntity<ProblemDetail> handleRuleViolation(DomainRuleViolation ex) {
    HttpStatus status = codeStatuses.getOrDefault(ex.code(), UNPROCESSABLE);
    return respond(status, ex.code(), "Business rule violated", ex.getMessage());
  }

  @ExceptionHandler(ResourceNotFound.class)
  ResponseEntity<ProblemDetail> handleNotFound(ResourceNotFound ex) {
    return respond(
        HttpStatus.NOT_FOUND, NOT_FOUND, ex.resourceType() + " not found", ex.getMessage());
  }

  @ExceptionHandler(Precondition.PreconditionFailed.class)
  ResponseEntity<ProblemDetail> handlePreconditionFailed(Precondition.PreconditionFailed ex) {
    return respond(
        HttpStatus.PRECONDITION_FAILED,
        PRECONDITION_FAILED,
        "Resource has changed",
        ex.getMessage() + "; re-read the resource and retry with its current ETag.");
  }

  @ExceptionHandler(OptimisticLockingFailureException.class)
  ResponseEntity<ProblemDetail> handleConcurrentModification(OptimisticLockingFailureException ex) {
    return respond(
        HttpStatus.CONFLICT,
        CONCURRENT_MODIFICATION,
        "Concurrent modification",
        "The resource was modified by another request; re-read it and retry.");
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
    return respond(
        HttpStatus.INTERNAL_SERVER_ERROR,
        INTERNAL_ERROR,
        "Internal error",
        "An unexpected error occurred. Quote the correlation ID when reporting it.");
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
      ProblemFactory.addCorrelationId(problem);
    }
    return super.createResponseEntity(body, headers, statusCode, request);
  }

  private ResponseEntity<ProblemDetail> validationProblem(List<Map<String, String>> violations) {
    ProblemDetail problem =
        problems.create(
            HttpStatus.BAD_REQUEST,
            VALIDATION_FAILED,
            "Request validation failed",
            "One or more fields are invalid.");
    problem.setProperty("violations", violations);
    return ResponseEntity.badRequest().body(problem);
  }

  private ResponseEntity<ProblemDetail> respond(
      HttpStatus status, String errorCode, String title, String detail) {
    return ResponseEntity.status(status).body(problems.create(status, errorCode, title, detail));
  }

  private static Map<String, String> violation(String field, String message) {
    return Map.of("field", field, "message", message == null ? "invalid" : message);
  }
}
