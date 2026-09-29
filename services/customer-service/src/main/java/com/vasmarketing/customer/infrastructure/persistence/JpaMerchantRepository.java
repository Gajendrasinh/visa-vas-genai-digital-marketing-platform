package com.vasmarketing.customer.infrastructure.persistence;

import com.vasmarketing.customer.domain.merchant.Merchant;
import com.vasmarketing.customer.domain.merchant.MerchantCategoryCode;
import com.vasmarketing.customer.domain.merchant.MerchantId;
import com.vasmarketing.customer.domain.merchant.MerchantRepository;
import java.util.Optional;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class JpaMerchantRepository implements MerchantRepository {

  private final MerchantJpaRepository jpa;

  JpaMerchantRepository(MerchantJpaRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Merchant> findById(MerchantId id) {
    return jpa.findById(id.value()).map(JpaMerchantRepository::toDomain);
  }

  @Override
  @Transactional
  public Merchant save(Merchant merchant) {
    MerchantEntity entity =
        merchant.version().map(v -> managedEntity(merchant, v)).orElseGet(MerchantEntity::new);
    entity.id = merchant.id().value();
    entity.name = merchant.name();
    entity.mcc = merchant.mcc().value();
    entity.category = merchant.category();
    entity.countryCode = merchant.countryCode();
    entity.city = merchant.city().orElse(null);
    entity.active = merchant.active();
    entity.createdAt = merchant.createdAt();
    entity.updatedAt = merchant.updatedAt();
    return toDomain(jpa.saveAndFlush(entity));
  }

  private MerchantEntity managedEntity(Merchant merchant, long expectedVersion) {
    MerchantEntity entity =
        jpa.findById(merchant.id().value())
            .orElseThrow(
                () ->
                    new ObjectOptimisticLockingFailureException(
                        MerchantEntity.class, merchant.id().value()));
    if (entity.version != expectedVersion) {
      throw new ObjectOptimisticLockingFailureException(MerchantEntity.class, entity.id);
    }
    return entity;
  }

  private static Merchant toDomain(MerchantEntity e) {
    return Merchant.restore(
        new Merchant.State(
            new MerchantId(e.id),
            e.name,
            new MerchantCategoryCode(e.mcc),
            e.countryCode,
            e.city,
            e.active,
            e.createdAt,
            e.updatedAt,
            e.version));
  }
}
