package com.vasmarketing.platform.web.correlation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

  private final CorrelationIdFilter filter = new CorrelationIdFilter();

  @Test
  void propagatesWellFormedInboundId() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(CorrelationId.HEADER, "req-12345678");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new MockFilterChain());

    assertThat(response.getHeader(CorrelationId.HEADER)).isEqualTo("req-12345678");
  }

  @Test
  void generatesIdWhenHeaderAbsent() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(new MockHttpServletRequest(), response, new MockFilterChain());

    assertThat(UUID.fromString(response.getHeader(CorrelationId.HEADER))).isNotNull();
  }

  @Test
  void replacesMalformedIdToPreventLogInjection() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(CorrelationId.HEADER, "abc\ninjected=true");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new MockFilterChain());

    assertThat(response.getHeader(CorrelationId.HEADER))
        .isNotEqualTo("abc\ninjected=true")
        .doesNotContain("\n");
  }

  @Test
  void bindsIdToMdcDuringRequestAndClearsItAfterwards() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(CorrelationId.HEADER, "req-abcdefgh");
    AtomicReference<String> seenInChain = new AtomicReference<>();

    filter.doFilter(
        request,
        new MockHttpServletResponse(),
        (req, res) -> seenInChain.set(CorrelationId.current().orElse(null)));

    assertThat(seenInChain.get()).isEqualTo("req-abcdefgh");
    assertThat(MDC.get(CorrelationId.MDC_KEY)).isNull();
  }

  @Test
  void resolveRejectsTooShortAndTooLongValues() {
    assertThat(CorrelationId.resolve("short")).isNotEqualTo("short");
    String tooLong = "a".repeat(65);
    assertThat(CorrelationId.resolve(tooLong)).isNotEqualTo(tooLong);
  }
}
