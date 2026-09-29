package com.vasmarketing.customer.application;

import com.vasmarketing.customer.domain.merchant.Merchant;
import com.vasmarketing.customer.domain.merchant.MerchantCategoryCode;
import com.vasmarketing.customer.domain.merchant.MerchantId;
import com.vasmarketing.customer.domain.merchant.MerchantRepository;
import com.vasmarketing.platform.types.ResourceNotFound;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Merchant reference data management (network-wide, not tenant-scoped). */
@Service
@Transactional
public class MerchantService {

  private final MerchantRepository repository;
  private final Clock clock;

  MerchantService(MerchantRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  public Merchant register(String name, String mcc, String countryCode, String city) {
    return repository.save(
        Merchant.register(name, new MerchantCategoryCode(mcc), countryCode, city, clock.instant()));
  }

  public Merchant update(MerchantId id, String name, String mcc, String city) {
    Merchant merchant = load(id);
    merchant.update(name, new MerchantCategoryCode(mcc), city, clock.instant());
    return repository.save(merchant);
  }

  public Merchant deactivate(MerchantId id) {
    Merchant merchant = load(id);
    merchant.deactivate(clock.instant());
    return repository.save(merchant);
  }

  @Transactional(readOnly = true)
  public Merchant get(MerchantId id) {
    return load(id);
  }

  private Merchant load(MerchantId id) {
    return repository.findById(id).orElseThrow(() -> new ResourceNotFound("Merchant", id));
  }
}
