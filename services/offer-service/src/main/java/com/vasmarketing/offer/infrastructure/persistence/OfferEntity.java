package com.vasmarketing.offer.infrastructure.persistence;

import com.vasmarketing.offer.domain.model.OfferCategory;
import com.vasmarketing.offer.domain.model.OfferStatus;
import com.vasmarketing.offer.domain.model.RewardType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "offers")
class OfferEntity {

  @Id UUID id;

  @Column(name = "tenant_id", updatable = false)
  UUID tenantId;

  @Column(name = "merchant_id")
  UUID merchantId;

  String title;
  String description;

  @Enumerated(EnumType.STRING)
  OfferCategory category;

  @Enumerated(EnumType.STRING)
  @Column(name = "reward_type")
  RewardType rewardType;

  @Column(name = "reward_percent")
  BigDecimal rewardPercent;

  @Column(name = "reward_fixed")
  BigDecimal rewardFixed;

  @Column(name = "max_reward")
  BigDecimal maxReward;

  @Column(name = "min_spend")
  BigDecimal minSpend;

  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(length = 3)
  String currency;

  @Column(name = "per_customer_cap")
  int perCustomerCap;

  @Column(name = "total_budget")
  BigDecimal totalBudget;

  @Column(name = "start_at")
  Instant startAt;

  @Column(name = "end_at")
  Instant endAt;

  @Enumerated(EnumType.STRING)
  OfferStatus status;

  @Column(name = "terms_document_id")
  UUID termsDocumentId;

  @Column(name = "rule_set_version")
  int ruleSetVersion;

  @Column(name = "created_by", updatable = false)
  String createdBy;

  @Column(name = "created_at", updatable = false)
  Instant createdAt;

  @Column(name = "updated_at")
  Instant updatedAt;

  @Version Long version;

  @ElementCollection
  @CollectionTable(name = "offer_eligibility_rules", joinColumns = @JoinColumn(name = "offer_id"))
  @OrderColumn(name = "position")
  List<RuleEmbeddable> rules = new ArrayList<>();
}
