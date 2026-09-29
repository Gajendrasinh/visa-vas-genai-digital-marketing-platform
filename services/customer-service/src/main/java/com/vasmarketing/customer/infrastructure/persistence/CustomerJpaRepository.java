package com.vasmarketing.customer.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Queries still filter by tenant explicitly; RLS is the second, independent layer. */
interface CustomerJpaRepository extends JpaRepository<CustomerEntity, UUID> {

  Optional<CustomerEntity> findByIdAndTenantId(UUID id, UUID tenantId);

  Optional<CustomerEntity> findByTenantIdAndEmailHmac(UUID tenantId, String emailHmac);
}
