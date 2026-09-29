package com.vasmarketing.customer.infrastructure.persistence;

import com.vasmarketing.platform.types.TenantId;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Binds the tenant to the current database transaction for Postgres row-level security. The setting
 * is transaction-local, so pooled connections never carry a tenant into the next request.
 */
@Component
class TenantSession {

  private final EntityManager entityManager;

  TenantSession(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  void bind(TenantId tenantId) {
    if (!TransactionSynchronizationManager.isActualTransactionActive()) {
      throw new IllegalStateException("tenant binding requires an active transaction");
    }
    entityManager
        .createNativeQuery("select set_config('app.tenant_id', :tenant, true)")
        .setParameter("tenant", tenantId.value().toString())
        .getSingleResult();
  }
}
