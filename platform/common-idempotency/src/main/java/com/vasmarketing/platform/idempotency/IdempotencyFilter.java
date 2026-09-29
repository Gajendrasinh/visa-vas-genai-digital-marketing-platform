package com.vasmarketing.platform.idempotency;

import com.vasmarketing.platform.web.error.ProblemFactory;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;
import tools.jackson.databind.ObjectWriter;

/**
 * Implements {@code Idempotency-Key} for POST requests. Runs after authentication: keys are scoped
 * to the authenticated principal, so one caller can never replay another's response.
 */
public final class IdempotencyFilter extends OncePerRequestFilter {

  public static final String HEADER = "Idempotency-Key";
  public static final String REPLAYED_HEADER = "Idempotent-Replayed";

  private static final Pattern KEY_FORMAT = Pattern.compile("^[A-Za-z0-9_-]{8,128}$");

  private final IdempotencyStore store;
  private final ProblemFactory problems;
  private final ObjectWriter writer;
  private final int maxBodyBytes;

  public IdempotencyFilter(
      IdempotencyStore store, ProblemFactory problems, ObjectWriter writer, int maxBodyBytes) {
    this.store = store;
    this.problems = problems;
    this.writer = writer;
    this.maxBodyBytes = maxBodyBytes;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !"POST".equals(request.getMethod()) || request.getHeader(HEADER) == null;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String key = request.getHeader(HEADER);
    Principal principal = request.getUserPrincipal();
    if (principal == null) {
      chain.doFilter(request, response); // unauthenticated: security rejects it downstream
      return;
    }
    if (!KEY_FORMAT.matcher(key).matches()) {
      writeProblem(
          response,
          HttpStatus.BAD_REQUEST,
          "INVALID_IDEMPOTENCY_KEY",
          "Invalid Idempotency-Key",
          "Idempotency-Key must be 8-128 characters of letters, digits, '-' or '_'.");
      return;
    }
    CachedBodyRequest cached;
    try {
      cached = CachedBodyRequest.wrap(request, maxBodyBytes);
    } catch (CachedBodyRequest.BodyTooLargeException e) {
      writeProblem(
          response,
          HttpStatus.CONTENT_TOO_LARGE,
          "REQUEST_TOO_LARGE",
          "Request too large",
          e.getMessage());
      return;
    }
    String scope = principal.getName();
    String hash = hash(request, cached.body());
    if (store.tryClaim(scope, key, hash)) {
      execute(cached, response, chain, scope, key);
    } else {
      respondToRepeat(response, store.find(scope, key), hash);
    }
  }

  private void execute(
      CachedBodyRequest request,
      HttpServletResponse response,
      FilterChain chain,
      String scope,
      String key)
      throws ServletException, IOException {
    ContentCachingResponseWrapper captured = new ContentCachingResponseWrapper(response);
    boolean completed = false;
    try {
      chain.doFilter(request, captured);
      if (captured.getStatus() < 500) {
        store.complete(
            scope,
            key,
            captured.getStatus(),
            captured.getContentType(),
            captured.getHeader(HttpHeaders.LOCATION),
            captured.getContentAsByteArray());
        completed = true;
      }
    } finally {
      if (!completed) {
        store.release(scope, key);
      }
      captured.copyBodyToResponse();
    }
  }

  private void respondToRepeat(
      HttpServletResponse response, Optional<IdempotencyStore.Entry> entry, String hash)
      throws IOException {
    if (entry.isEmpty()) {
      // Released between our claim attempt and lookup: the client may simply retry.
      writeRetry(response);
      return;
    }
    IdempotencyStore.Entry stored = entry.get();
    if (!stored.requestHash().equals(hash)) {
      writeProblem(
          response,
          HttpStatus.valueOf(422),
          "IDEMPOTENCY_KEY_REUSED",
          "Idempotency-Key reused",
          "This Idempotency-Key was already used with a different request.");
    } else if (!stored.completed()) {
      writeRetry(response);
    } else {
      response.setStatus(stored.responseStatus());
      response.setHeader(REPLAYED_HEADER, "true");
      if (stored.contentType() != null) {
        response.setContentType(stored.contentType());
      }
      if (stored.location() != null) {
        response.setHeader(HttpHeaders.LOCATION, stored.location());
      }
      byte[] body = stored.body();
      if (body != null) {
        response.getOutputStream().write(body);
      }
    }
  }

  private void writeRetry(HttpServletResponse response) throws IOException {
    response.setHeader(HttpHeaders.RETRY_AFTER, "1");
    writeProblem(
        response,
        HttpStatus.CONFLICT,
        "IDEMPOTENCY_REQUEST_IN_PROGRESS",
        "Request in progress",
        "A request with this Idempotency-Key is still being processed; retry shortly.");
  }

  private void writeProblem(
      HttpServletResponse response, HttpStatus status, String code, String title, String detail)
      throws IOException {
    ProblemDetail problem = problems.create(status, code, title, detail);
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    writer.writeValue(response.getOutputStream(), problem);
  }

  /** Binds the key to the exact request: method, path, query and body. */
  private static String hash(HttpServletRequest request, byte[] body) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      String target =
          request.getMethod()
              + " "
              + request.getRequestURI()
              + (request.getQueryString() == null ? "" : "?" + request.getQueryString())
              + "\n";
      digest.update(target.getBytes(StandardCharsets.UTF_8));
      digest.update(body);
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }
}
