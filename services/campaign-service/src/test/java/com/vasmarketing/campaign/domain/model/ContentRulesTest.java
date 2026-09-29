package com.vasmarketing.campaign.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vasmarketing.platform.types.DomainRuleViolation;
import com.vasmarketing.platform.types.Money;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class ContentRulesTest {

  @Test
  void acceptsAllowListedSlots() {
    assertThat(
            TemplateSlots.validate(
                "Hi {{ customer.firstName }}, {{offer.value}} at {{merchant.name}}"))
        .containsExactly("customer.firstName", "offer.value", "merchant.name");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "Your balance is {{account.balance}}",
        "Hello {{customer.email}}",
        "Broken {{offer.value",
        "Broken offer.value}}"
      })
  void rejectsUnknownOrMalformedSlots(String template) {
    assertThatThrownBy(() -> TemplateSlots.validate(template))
        .isInstanceOf(DomainRuleViolation.class)
        .extracting("code")
        .isEqualTo(CampaignErrors.INVALID_VARIANT);
  }

  @Test
  void enforcesChannelLengthLimits() {
    String longSms = "x".repeat(Channel.SMS.maxBodyLength() + 1);
    assertThatThrownBy(() -> new VariantContent(Channel.SMS, "A", "en", null, longSms))
        .hasMessageContaining("160");
    assertThat(new VariantContent(Channel.SMS, "A", "en", null, "x".repeat(160)).bodyTemplate())
        .hasSize(160);
  }

  @Test
  void subjectRequiredForEmailAndForbiddenElsewhere() {
    assertThatThrownBy(() -> new VariantContent(Channel.EMAIL, "A", "en", null, "body"))
        .hasMessageContaining("subject");
    assertThatThrownBy(() -> new VariantContent(Channel.PUSH, "A", "en", "subject", "body"))
        .hasMessageContaining("no subject");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "has space", "way-too-long-variant-key-over-32-chars"})
  void validatesVariantKey(String key) {
    assertThatThrownBy(() -> new VariantContent(Channel.PUSH, key, "en", null, "body"))
        .hasMessageContaining("variantKey");
  }

  @Test
  void validatesLanguageTag() {
    assertThatThrownBy(() -> new VariantContent(Channel.PUSH, "A", "english", null, "body"))
        .hasMessageContaining("language");
    assertThat(new VariantContent(Channel.PUSH, "A", "en-GB", null, "body").language())
        .isEqualTo("en-GB");
  }

  @Test
  void scheduleMustEndAfterStart() {
    Instant t = Instant.parse("2026-10-01T00:00:00Z");
    assertThatThrownBy(() -> new Schedule(t, t))
        .extracting("code")
        .isEqualTo(CampaignErrors.INVALID_SCHEDULE);
  }

  @Test
  void detailsValidateRequiredFields() {
    Schedule schedule = new Schedule(Instant.EPOCH, Instant.EPOCH.plusSeconds(1));
    UUID id = UUID.randomUUID();
    Money budget = Money.of("1", "USD");

    assertThatThrownBy(
            () -> new CampaignDetails("", "o", id, id, schedule, budget, Set.of(Channel.SMS)))
        .hasMessageContaining("name");
    assertThatThrownBy(() -> new CampaignDetails("n", "o", id, id, schedule, budget, Set.of()))
        .hasMessageContaining("channel");
    assertThatThrownBy(
            () ->
                new CampaignDetails(
                    "n", "o", id, id, schedule, Money.of("-1", "USD"), Set.of(Channel.SMS)))
        .hasMessageContaining("budget");
  }

  @ParameterizedTest
  @EnumSource(CampaignStatus.class)
  void onlyActiveCampaignsAreLive(CampaignStatus status) {
    assertThat(status.isLive()).isEqualTo(status == CampaignStatus.ACTIVE);
  }

  @Test
  void archivedIsTerminal() {
    for (CampaignStatus target : EnumSet.allOf(CampaignStatus.class)) {
      assertThat(CampaignStatus.ARCHIVED.canTransitionTo(target)).isFalse();
    }
  }
}
