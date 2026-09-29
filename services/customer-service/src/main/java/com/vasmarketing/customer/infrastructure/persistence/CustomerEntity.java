package com.vasmarketing.customer.infrastructure.persistence;

import com.vasmarketing.customer.domain.customer.CustomerStatus;
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

/** Holds ciphertext only; plaintext PII exists solely in the domain object. */
@Entity
@Table(name = "customers")
class CustomerEntity {

  @Id UUID id;

  @Column(name = "tenant_id", updatable = false)
  UUID tenantId;

  @Column(name = "full_name_enc")
  byte[] fullNameEnc;

  @Column(name = "email_enc")
  byte[] emailEnc;

  @Column(name = "phone_enc")
  byte[] phoneEnc;

  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(name = "email_hmac", length = 64)
  String emailHmac;

  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(name = "country_code", length = 2)
  String countryCode;

  @Column(name = "birth_year")
  short birthYear;

  @Column(name = "email_opt_in")
  boolean emailOptIn;

  @Column(name = "push_opt_in")
  boolean pushOptIn;

  @Column(name = "sms_opt_in")
  boolean smsOptIn;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "preferred_categories")
  String[] preferredCategories;

  String language;

  @Column(name = "marketing_consent")
  boolean marketingConsent;

  @Column(name = "consent_updated_at")
  Instant consentUpdatedAt;

  @Enumerated(EnumType.STRING)
  CustomerStatus status;

  @Column(name = "created_at", updatable = false)
  Instant createdAt;

  @Column(name = "updated_at")
  Instant updatedAt;

  @Version Long version;
}
