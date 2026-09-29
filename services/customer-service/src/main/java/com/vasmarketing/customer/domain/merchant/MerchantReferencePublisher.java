package com.vasmarketing.customer.domain.merchant;

/**
 * Publishes the latest state of a merchant to the compacted reference topic, through the caller's
 * transaction (transactional outbox).
 */
public interface MerchantReferencePublisher {

  void publish(Merchant merchant);
}
