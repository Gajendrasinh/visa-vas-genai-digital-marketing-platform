package com.vasmarketing.campaign.domain.model;

import com.vasmarketing.platform.types.UserId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Approval(
    UUID id, ApprovalDecision decision, UserId decidedBy, String comment, Instant decidedAt) {

  public static final int MAX_COMMENT_LENGTH = 1_000;

  public Approval {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(decision, "decision");
    Objects.requireNonNull(decidedBy, "decidedBy");
    Objects.requireNonNull(decidedAt, "decidedAt");
    if (comment != null && comment.length() > MAX_COMMENT_LENGTH) {
      throw new IllegalArgumentException("comment too long");
    }
  }
}
