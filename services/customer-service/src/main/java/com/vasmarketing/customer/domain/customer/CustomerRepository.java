package com.vasmarketing.customer.domain.customer;

import com.vasmarketing.platform.types.TenantId;
import java.util.Optional;

/** Persistence port. Implementations encrypt PII and enforce tenant isolation. */
public interface CustomerRepository {

  Optional<Customer> findById(TenantId tenantId, CustomerId id);

  /** Looks up by email without decrypting stored data (keyed-hash index). */
  Optional<Customer> findByEmail(TenantId tenantId, String email);

  /** Inserts or updates; fails with a concurrency conflict on a stale version. */
  Customer save(Customer customer);
}
