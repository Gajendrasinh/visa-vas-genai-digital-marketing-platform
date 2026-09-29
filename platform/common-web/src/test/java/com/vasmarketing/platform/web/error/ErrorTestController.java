package com.vasmarketing.platform.web.error;

import com.vasmarketing.platform.types.DomainRuleViolation;
import com.vasmarketing.platform.types.Precondition;
import com.vasmarketing.platform.types.ResourceNotFound;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.Optional;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/test")
class ErrorTestController {

  record Payload(@NotBlank String name, @Min(1) int size) {}

  static final class InvalidTransitionException extends PlatformException {
    private static final long serialVersionUID = 1L;

    InvalidTransitionException(String detail) {
      super(HttpStatus.CONFLICT, "TEST_INVALID_TRANSITION", "Invalid transition", detail);
    }
  }

  @GetMapping("/conflict")
  String conflict() {
    throw new InvalidTransitionException("Campaign is DRAFT");
  }

  @PostMapping("/validated")
  String validated(@Valid @RequestBody Payload payload) {
    return payload.name();
  }

  @GetMapping("/limited")
  String limited(@RequestParam @Max(100) int limit) {
    return Integer.toString(limit);
  }

  @GetMapping("/rule/{code}")
  String rule(@PathVariable String code) {
    throw new DomainRuleViolation(code, "rule " + code + " violated");
  }

  @GetMapping("/missing")
  String missing() {
    throw new ResourceNotFound("Campaign", "42");
  }

  @GetMapping("/stale")
  String stale() {
    Precondition.ifMatch(3).check(Optional.of(4L));
    return "unreachable";
  }

  @GetMapping("/race")
  String race() {
    throw new OptimisticLockingFailureException("version changed");
  }

  @GetMapping("/boom")
  String boom() {
    throw new IllegalStateException("secret db host 10.0.0.5");
  }
}
