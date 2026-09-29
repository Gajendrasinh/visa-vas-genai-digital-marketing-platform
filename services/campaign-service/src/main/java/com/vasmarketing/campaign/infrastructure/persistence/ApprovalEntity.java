package com.vasmarketing.campaign.infrastructure.persistence;

import com.vasmarketing.campaign.domain.model.ApprovalDecision;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Approvals are append-only; existing rows are never updated. */
@Entity
@Table(name = "campaign_approvals")
class ApprovalEntity {

  @Id UUID id;

  @Enumerated(EnumType.STRING)
  @Column(updatable = false)
  ApprovalDecision decision;

  @Column(name = "decided_by", updatable = false)
  String decidedBy;

  @Column(updatable = false)
  String comment;

  @Column(name = "decided_at", updatable = false)
  Instant decidedAt;
}
