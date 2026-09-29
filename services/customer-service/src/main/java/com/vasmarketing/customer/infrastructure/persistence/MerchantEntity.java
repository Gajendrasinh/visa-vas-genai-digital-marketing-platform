package com.vasmarketing.customer.infrastructure.persistence;

import com.vasmarketing.customer.domain.customer.SpendCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "merchants")
class MerchantEntity {

  @Id UUID id;

  String name;

  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(length = 4)
  String mcc;

  /** Denormalized from the MCC for indexed category lookups. */
  @Enumerated(EnumType.STRING)
  SpendCategory category;

  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(name = "country_code", length = 2)
  String countryCode;

  String city;
  boolean active;

  @Column(name = "created_at", updatable = false)
  Instant createdAt;

  @Column(name = "updated_at")
  Instant updatedAt;

  @Version Long version;
}
