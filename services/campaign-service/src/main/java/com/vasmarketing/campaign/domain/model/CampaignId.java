package com.vasmarketing.campaign.domain.model;

import com.vasmarketing.platform.types.UuidV7;
import java.util.Objects;
import java.util.UUID;

public record CampaignId(UUID value) {

  public CampaignId {
    Objects.requireNonNull(value, "campaign id");
  }

  public static CampaignId newId() {
    return new CampaignId(UuidV7.generate());
  }

  @Override
  public String toString() {
    return value.toString();
  }
}
