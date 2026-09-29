package com.vasmarketing.campaign.infrastructure.persistence;

import com.vasmarketing.campaign.domain.model.Channel;
import com.vasmarketing.campaign.domain.model.ContentAuthor;
import com.vasmarketing.campaign.domain.model.VariantStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "content_variants")
class ContentVariantEntity {

  @Id UUID id;

  @Enumerated(EnumType.STRING)
  Channel channel;

  @Column(name = "variant_key")
  String variantKey;

  String language;
  String subject;

  @Column(name = "body_template")
  String bodyTemplate;

  @Enumerated(EnumType.STRING)
  VariantStatus status;

  @Enumerated(EnumType.STRING)
  @Column(name = "generated_by")
  ContentAuthor generatedBy;

  @Column(name = "prompt_id")
  String promptId;

  @Column(name = "prompt_version")
  String promptVersion;

  @Column(name = "model_id")
  String modelId;

  @Column(name = "compliance_report")
  String complianceReport;
}
