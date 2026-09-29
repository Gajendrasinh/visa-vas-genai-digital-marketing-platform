package com.vasmarketing.customer.domain.merchant;

import java.util.Optional;

public interface MerchantRepository {

  Optional<Merchant> findById(MerchantId id);

  Merchant save(Merchant merchant);
}
