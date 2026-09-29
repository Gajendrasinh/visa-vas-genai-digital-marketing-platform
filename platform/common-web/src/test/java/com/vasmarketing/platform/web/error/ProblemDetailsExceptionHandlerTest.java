package com.vasmarketing.platform.web.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vasmarketing.platform.web.correlation.CorrelationId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest
@Import(ErrorTestController.class)
class ProblemDetailsExceptionHandlerTest {

  private static final String CORRELATION_ID = "test-corr-0001";

  @Autowired private MockMvc mvc;

  @Test
  void mapsPlatformExceptionToProblemDetail() throws Exception {
    mvc.perform(get("/test/conflict").header(CorrelationId.HEADER, CORRELATION_ID))
        .andExpect(status().isConflict())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(header().string(CorrelationId.HEADER, CORRELATION_ID))
        .andExpect(
            jsonPath("$.type").value("https://errors.vasmarketing.example/test-invalid-transition"))
        .andExpect(jsonPath("$.title").value("Invalid transition"))
        .andExpect(jsonPath("$.status").value(409))
        .andExpect(jsonPath("$.detail").value("Campaign is DRAFT"))
        .andExpect(jsonPath("$.errorCode").value("TEST_INVALID_TRANSITION"))
        .andExpect(jsonPath("$.correlationId").value(CORRELATION_ID));
  }

  @Test
  void reportsEachInvalidFieldForBodyValidation() throws Exception {
    mvc.perform(
            post("/test/validated")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\",\"size\":0}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.violations.length()").value(2))
        .andExpect(jsonPath("$.violations[?(@.field == 'name')]").exists())
        .andExpect(jsonPath("$.violations[?(@.field == 'size')]").exists());
  }

  @Test
  void reportsMethodParameterConstraintViolations() throws Exception {
    mvc.perform(get("/test/limited").param("limit", "500"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
  }

  @Test
  void hidesInternalDetailsOfUnexpectedErrors() throws Exception {
    mvc.perform(get("/test/boom").header(CorrelationId.HEADER, CORRELATION_ID))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"))
        .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.not("secret db host 10.0.0.5")))
        .andExpect(jsonPath("$.correlationId").value(CORRELATION_ID));
  }

  @Test
  void enrichesSpringGeneratedProblemsWithCorrelationId() throws Exception {
    mvc.perform(post("/test/conflict").header(CorrelationId.HEADER, CORRELATION_ID))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(jsonPath("$.correlationId").value(CORRELATION_ID));
  }
}
