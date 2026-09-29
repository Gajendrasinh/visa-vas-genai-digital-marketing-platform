package com.vasmarketing.platform.types;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TypesTest {

  @Test
  void uuidV7HasVersionVariantAndTimestamp() {
    Instant now = Instant.parse("2026-09-29T10:00:00Z");
    UUID id = UuidV7.generate(Clock.fixed(now, ZoneOffset.UTC));

    assertThat(id.version()).isEqualTo(7);
    assertThat(id.variant()).isEqualTo(2);
    assertThat(id.getMostSignificantBits() >>> 16).isEqualTo(now.toEpochMilli());
  }

  @Test
  void uuidV7IsOrderedAcrossMilliseconds() {
    UUID earlier = UuidV7.generate(Clock.fixed(Instant.ofEpochMilli(1_000), ZoneOffset.UTC));
    UUID later = UuidV7.generate(Clock.fixed(Instant.ofEpochMilli(2_000), ZoneOffset.UTC));

    assertThat(earlier.toString()).isLessThan(later.toString());
  }

  @Test
  void moneyNormalizesScaleAndDoesArithmetic() {
    Money a = Money.of("10.5", "USD");
    Money b = Money.of("0.25", "USD");

    assertThat(a.plus(b)).isEqualTo(Money.of("10.7500", "USD"));
    assertThat(b.minus(a).isNegative()).isTrue();
    assertThat(a.compareTo(b)).isPositive();
    assertThat(Money.zero(a.currency()).isPositive()).isFalse();
  }

  @Test
  void moneyRejectsCurrencyMismatch() {
    assertThatThrownBy(() -> Money.of("1", "USD").plus(Money.of("1", "EUR")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("currency mismatch");
  }

  @Test
  void userIdValidatesLength() {
    assertThatThrownBy(() -> new UserId(" ")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new UserId("x".repeat(65)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(new UserId("user-1")).hasToString("user-1");
  }

  @Test
  void identifiersRoundTrip() {
    UUID raw = UUID.randomUUID();
    TenantId tenant = TenantId.of(raw.toString());

    assertThat(tenant.value()).isEqualTo(raw);
    assertThat(tenant).hasToString(raw.toString());
    assertThat(new Actor(new UserId("u"), tenant).tenantId()).isEqualTo(tenant);
  }

  @Test
  void domainRuleViolationCarriesCode() {
    DomainRuleViolation violation = new DomainRuleViolation("SOME_RULE", "explained");

    assertThat(violation.code()).isEqualTo("SOME_RULE");
    assertThat(violation).hasMessage("explained");
  }

  @Test
  void resourceNotFoundNamesTheResource() {
    ResourceNotFound notFound = new ResourceNotFound("Campaign", "42");

    assertThat(notFound.resourceType()).isEqualTo("Campaign");
    assertThat(notFound).hasMessage("Campaign 42 not found");
  }
}
