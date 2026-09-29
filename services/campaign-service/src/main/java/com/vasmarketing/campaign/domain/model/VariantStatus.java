package com.vasmarketing.campaign.domain.model;

public enum VariantStatus {
  DRAFT,
  COMPLIANCE_PASSED,
  COMPLIANCE_FAILED,
  /** Approved together with the campaign; only APPROVED variants are ever sent. */
  APPROVED
}
