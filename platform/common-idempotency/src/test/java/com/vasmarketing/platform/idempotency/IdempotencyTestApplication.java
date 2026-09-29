package com.vasmarketing.platform.idempotency;

import java.net.URI;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@SpringBootConfiguration
@EnableAutoConfiguration
@Import(IdempotencyTestApplication.CountingController.class)
public class IdempotencyTestApplication {

  @RestController
  static class CountingController {

    static final AtomicInteger CALLS = new AtomicInteger();

    @Idempotent
    @PostMapping("/things")
    ResponseEntity<String> create(@RequestBody String body) {
      int call = CALLS.incrementAndGet();
      return ResponseEntity.created(URI.create("/things/" + call)).body("{\"call\":" + call + "}");
    }

    @Idempotent
    @PostMapping("/failing")
    String failing() {
      CALLS.incrementAndGet();
      throw new IllegalStateException("downstream unavailable");
    }
  }
}
