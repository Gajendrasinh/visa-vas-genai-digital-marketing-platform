package com.vasmarketing.campaign.infrastructure.persistence;

import com.vasmarketing.campaign.domain.model.CampaignOrigin;
import com.vasmarketing.campaign.domain.model.CampaignStatus;
import com.vasmarketing.campaign.domain.model.Channel;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** JPA mapping of the campaign aggregate. Not a domain object; see {@link CampaignMapper}. */
@Entity
@Table(name = "campaigns")
class CampaignEntity {

  @Id UUID id;

  @Column(name = "tenant_id", nullable = false, updatable = false)
  UUID tenantId;

  String name;
  String objective;

  @Enumerated(EnumType.STRING)
  CampaignStatus status;

  @Column(name = "segment_id")
  UUID segmentId;

  @Column(name = "offer_id")
  UUID offerId;

  @Column(name = "start_at")
  Instant startAt;

  @Column(name = "end_at")
  Instant endAt;

  @Column(name = "budget_amount")
  BigDecimal budgetAmount;

  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(length = 3)
  String currency;

  @Enumerated(EnumType.STRING)
  @Column(updatable = false)
  CampaignOrigin origin;

  @Column(name = "ai_request_id", updatable = false)
  UUID aiRequestId;

  @Column(name = "created_by", updatable = false)
  String createdBy;

  @Column(name = "published_by")
  String publishedBy;

  @Column(name = "published_at")
  Instant publishedAt;

  @Column(name = "created_at", updatable = false)
  Instant createdAt;

  @Column(name = "updated_at")
  Instant updatedAt;

  @Version Long version;

  @ElementCollection
  @CollectionTable(name = "campaign_channels", joinColumns = @JoinColumn(name = "campaign_id"))
  @Column(name = "channel")
  @Enumerated(EnumType.STRING)
  Set<Channel> channels = EnumSet.noneOf(Channel.class);

  @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
  @JoinColumn(name = "campaign_id", nullable = false)
  @OrderBy("id")
  List<ContentVariantEntity> variants = new ArrayList<>();

  @OneToMany(cascade = CascadeType.ALL)
  @JoinColumn(name = "campaign_id", nullable = false)
  @OrderBy("decidedAt")
  List<ApprovalEntity> approvals = new ArrayList<>();
}
