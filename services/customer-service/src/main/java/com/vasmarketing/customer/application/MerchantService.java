package com.vasmarketing.customer.application;

import com.vasmarketing.customer.domain.merchant.Merchant;
import com.vasmarketing.customer.domain.merchant.MerchantCategoryCode;
import com.vasmarketing.customer.domain.merchant.MerchantId;
import com.vasmarketing.customer.domain.merchant.MerchantReferencePublisher;
import com.vasmarketing.customer.domain.merchant.MerchantRepository;
import com.vasmarketing.platform.types.Precondition;
import com.vasmarketing.platform.types.ResourceNotFound;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Merchant reference data management (network-wide, not tenant-scoped). */
@Service
@Transactional
public class MerchantService {

  private final MerchantRepository repository;
  private final MerchantReferencePublisher reference;
  private final Clock clock;

  MerchantService(
      MerchantRepository repository, MerchantReferencePublisher reference, Clock clock) {
    this.repository = repository;
    this.reference = reference;
    this.clock = clock;
  }

  public Merchant register(String name, String mcc, String countryCode, String city) {
    return saveAndPublish(
        Merchant.register(name, new MerchantCategoryCode(mcc), countryCode, city, clock.instant()));
  }

  public Merchant update(
      MerchantId id, String name, String mcc, String city, Precondition precondition) {
    Merchant merchant = loadForUpdate(id, precondition);
    merchant.update(name, new MerchantCategoryCode(mcc), city, clock.instant());
    return saveAndPublish(merchant);
  }

  public Merchant deactivate(MerchantId id, Precondition precondition) {
    Merchant merchant = loadForUpdate(id, precondition);
    merchant.deactivate(clock.instant());
    return saveAndPublish(merchant);
  }

  @Transactional(readOnly = true)
  public Merchant get(MerchantId id) {
    return load(id);
  }

  private Merchant saveAndPublish(Merchant merchant) {
    Merchant saved = repository.save(merchant);
    reference.publish(saved);
    return saved;
  }

  private Merchant loadForUpdate(MerchantId id, Precondition precondition) {
    Merchant merchant = load(id);
    precondition.check(merchant.version());
    return merchant;
  }

  private Merchant load(MerchantId id) {
    return repository.findById(id).orElseThrow(() -> new ResourceNotFound("Merchant", id));
  }
}
