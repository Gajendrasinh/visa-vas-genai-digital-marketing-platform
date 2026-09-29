package com.vasmarketing.campaign;

import com.vasmarketing.campaign.domain.model.CampaignDetails;
import com.vasmarketing.campaign.domain.model.Channel;
import com.vasmarketing.campaign.domain.model.Schedule;
import com.vasmarketing.campaign.domain.model.VariantContent;
import com.vasmarketing.platform.types.Actor;
import com.vasmarketing.platform.types.Money;
import com.vasmarketing.platform.types.TenantId;
import com.vasmarketing.platform.types.UserId;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public final class CampaignFixtures {

  public static final Instant NOW = Instant.parse("2026-10-01T09:00:00Z");
  public static final TenantId TENANT =
      new TenantId(UUID.fromString("0191f000-0000-7000-8000-000000000001"));
  public static final Actor CREATOR = new Actor(new UserId("manager"), TENANT);
  public static final Actor APPROVER = new Actor(new UserId("approver"), TENANT);

  private CampaignFixtures() {}

  public static CampaignDetails details(String name, Channel... channels) {
    return new CampaignDetails(
        name,
        "Grow travel spend among frequent travelers",
        UUID.fromString("0191f000-0000-7000-8000-00000000a001"),
        UUID.fromString("0191f000-0000-7000-8000-00000000b001"),
        new Schedule(NOW.plusSeconds(3_600), NOW.plusSeconds(30 * 86_400L)),
        Money.of("25000", "USD"),
        Set.of(channels));
  }

  public static VariantContent email(String key) {
    return new VariantContent(
        Channel.EMAIL,
        key,
        "en",
        "{{customer.firstName}}, earn {{offer.value}} back",
        "Book with {{merchant.name}} before {{offer.endDate}} and get {{offer.value}} back.");
  }

  public static VariantContent push(String key) {
    return new VariantContent(
        Channel.PUSH, key, "en", null, "{{offer.value}} back on {{merchant.name}} bookings");
  }
}
