package com.vasmarketing.customer.infrastructure.persistence;

import com.vasmarketing.customer.domain.customer.Customer;
import com.vasmarketing.customer.domain.customer.CustomerErrors;
import com.vasmarketing.customer.domain.customer.CustomerId;
import com.vasmarketing.customer.domain.customer.CustomerRepository;
import com.vasmarketing.customer.domain.customer.PersonalData;
import com.vasmarketing.customer.domain.customer.Preferences;
import com.vasmarketing.customer.domain.customer.SpendCategory;
import com.vasmarketing.customer.infrastructure.crypto.EmailFingerprint;
import com.vasmarketing.customer.infrastructure.crypto.FieldCipher;
import com.vasmarketing.platform.types.DomainRuleViolation;
import com.vasmarketing.platform.types.TenantId;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class JpaCustomerRepository implements CustomerRepository {

  private static final String EMAIL_CONSTRAINT = "customers_tenant_email_uk";

  private final CustomerJpaRepository jpa;
  private final TenantSession tenantSession;
  private final FieldCipher cipher;
  private final EmailFingerprint fingerprint;

  JpaCustomerRepository(
      CustomerJpaRepository jpa,
      TenantSession tenantSession,
      FieldCipher cipher,
      EmailFingerprint fingerprint) {
    this.jpa = jpa;
    this.tenantSession = tenantSession;
    this.cipher = cipher;
    this.fingerprint = fingerprint;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Customer> findById(TenantId tenantId, CustomerId id) {
    tenantSession.bind(tenantId);
    return jpa.findByIdAndTenantId(id.value(), tenantId.value()).map(this::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Customer> findByEmail(TenantId tenantId, String email) {
    tenantSession.bind(tenantId);
    return jpa.findByTenantIdAndEmailHmac(
            tenantId.value(), fingerprint.of(tenantId.toString(), email))
        .map(this::toDomain);
  }

  @Override
  @Transactional
  public Customer save(Customer customer) {
    tenantSession.bind(customer.tenantId());
    CustomerEntity entity =
        customer.version().map(v -> managedEntity(customer, v)).orElseGet(CustomerEntity::new);
    apply(customer, entity);
    try {
      return toDomain(jpa.saveAndFlush(entity));
    } catch (DataIntegrityViolationException e) {
      String cause = e.getMostSpecificCause().getMessage();
      if (cause != null && cause.contains(EMAIL_CONSTRAINT)) {
        throw new DomainRuleViolation(
            CustomerErrors.DUPLICATE_EMAIL, "a customer with this email already exists");
      }
      throw e;
    }
  }

  private CustomerEntity managedEntity(Customer customer, long expectedVersion) {
    CustomerEntity entity =
        jpa.findByIdAndTenantId(customer.id().value(), customer.tenantId().value())
            .orElseThrow(
                () ->
                    new ObjectOptimisticLockingFailureException(
                        CustomerEntity.class, customer.id().value()));
    if (entity.version != expectedVersion) {
      throw new ObjectOptimisticLockingFailureException(CustomerEntity.class, entity.id);
    }
    return entity;
  }

  private void apply(Customer customer, CustomerEntity e) {
    PersonalData pii = customer.personalData();
    e.id = customer.id().value();
    e.tenantId = customer.tenantId().value();
    e.fullNameEnc = cipher.encrypt(pii.fullName(), aad(e, "full_name"));
    e.emailEnc = cipher.encrypt(pii.email(), aad(e, "email"));
    e.phoneEnc = pii.phoneNumber().map(p -> cipher.encrypt(p, aad(e, "phone"))).orElse(null);
    e.emailHmac = fingerprint.of(customer.tenantId().toString(), pii.email());
    e.countryCode = customer.countryCode();
    e.birthYear = (short) customer.birthYear();
    Preferences prefs = customer.preferences();
    e.emailOptIn = prefs.emailOptIn();
    e.pushOptIn = prefs.pushOptIn();
    e.smsOptIn = prefs.smsOptIn();
    e.preferredCategories =
        prefs.preferredCategories().stream().map(Enum::name).sorted().toArray(String[]::new);
    e.language = prefs.language();
    e.marketingConsent = customer.marketingConsent();
    e.consentUpdatedAt = customer.consentUpdatedAt().orElse(null);
    e.status = customer.status();
    e.createdAt = customer.createdAt();
    e.updatedAt = customer.updatedAt();
  }

  private Customer toDomain(CustomerEntity e) {
    PersonalData pii =
        new PersonalData(
            cipher.decrypt(e.fullNameEnc, aad(e, "full_name")),
            cipher.decrypt(e.emailEnc, aad(e, "email")),
            e.phoneEnc == null ? null : cipher.decrypt(e.phoneEnc, aad(e, "phone")));
    Preferences prefs =
        new Preferences(
            e.emailOptIn,
            e.pushOptIn,
            e.smsOptIn,
            Arrays.stream(e.preferredCategories)
                .map(SpendCategory::valueOf)
                .collect(Collectors.toSet()),
            e.language);
    return Customer.restore(
        new Customer.State(
            new CustomerId(e.id),
            new TenantId(e.tenantId),
            pii,
            e.countryCode,
            e.birthYear,
            prefs,
            e.marketingConsent,
            e.consentUpdatedAt,
            e.status,
            e.createdAt,
            e.updatedAt,
            e.version));
  }

  /** Associated data binding a ciphertext to tenant, row and column. */
  private static String aad(CustomerEntity e, String column) {
    UUID tenant = e.tenantId;
    return "customers:" + tenant + ":" + e.id + ":" + column;
  }
}
