package com.vasmarketing.platform.web.correlation;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Accepts or generates the request correlation ID, binds it to the logging context and echoes it on
 * the response.
 */
public class CorrelationIdFilter extends OncePerRequestFilter {

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String correlationId = CorrelationId.resolve(request.getHeader(CorrelationId.HEADER));
    MDC.put(CorrelationId.MDC_KEY, correlationId);
    response.setHeader(CorrelationId.HEADER, correlationId);
    try {
      chain.doFilter(request, response);
    } finally {
      MDC.remove(CorrelationId.MDC_KEY);
    }
  }
}
