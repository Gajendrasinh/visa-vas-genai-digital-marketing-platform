package com.vasmarketing.campaign.domain.model;

/** Delivery channels with their content constraints (lengths apply to the unfilled template). */
public enum Channel {
  EMAIL(5_000, true),
  PUSH(240, false),
  IN_APP(500, false),
  SMS(160, false);

  private final int maxBodyLength;
  private final boolean subjectRequired;

  Channel(int maxBodyLength, boolean subjectRequired) {
    this.maxBodyLength = maxBodyLength;
    this.subjectRequired = subjectRequired;
  }

  public int maxBodyLength() {
    return maxBodyLength;
  }

  public boolean subjectRequired() {
    return subjectRequired;
  }
}
