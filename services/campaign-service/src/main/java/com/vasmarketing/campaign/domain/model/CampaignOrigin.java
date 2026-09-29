package com.vasmarketing.campaign.domain.model;

public enum CampaignOrigin {
  MANUAL,
  /** Drafted by the AI campaign agent; always starts as DRAFT and needs human approval. */
  AI_ASSISTED
}
