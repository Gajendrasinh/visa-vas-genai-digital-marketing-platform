package com.vasmarketing.platform.security;

import com.vasmarketing.platform.types.Actor;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootConfiguration
@EnableAutoConfiguration
@Import(SecurityTestApplication.ProbeController.class)
public class SecurityTestApplication {

  @RestController
  static class ProbeController {

    @GetMapping("/probe/whoami")
    String whoami(Actor actor) {
      return actor.userId() + "@" + actor.tenantId();
    }

    @GetMapping("/probe/publish")
    @PreAuthorize("hasAuthority('" + Permissions.CAMPAIGN_PUBLISH + "')")
    String publish() {
      return "published";
    }
  }
}
