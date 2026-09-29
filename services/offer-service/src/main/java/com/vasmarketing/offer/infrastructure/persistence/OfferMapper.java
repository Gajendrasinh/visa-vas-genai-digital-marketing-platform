package com.vasmarketing.offer.infrastructure.persistence;

import com.vasmarketing.offer.domain.model.Offer;
import com.vasmarketing.offer.domain.model.OfferId;
import com.vasmarketing.offer.domain.model.OfferTerms;
import com.vasmarketing.offer.domain.model.Reward;
import com.vasmarketing.offer.domain.model.Validity;
import com.vasmarketing.platform.types.Money;
import com.vasmarketing.platform.types.TenantId;
import com.vasmarketing.platform.types.UserId;
import java.util.Currency;

final class OfferMapper {

  private OfferMapper() {}

  static Offer toDomain(OfferEntity e) {
    Currency currency = Currency.getInstance(e.currency);
    Reward reward =
        new Reward(
            e.rewardType,
            e.rewardPercent,
            e.rewardFixed == null ? null : new Money(e.rewardFixed, currency),
            new Money(e.maxReward, currency));
    OfferTerms terms =
        new OfferTerms(
            e.merchantId,
            e.title,
            e.description,
            e.category,
            reward,
            new Money(e.minSpend, currency),
            e.perCustomerCap,
            new Money(e.totalBudget, currency),
            new Validity(e.startAt, e.endAt),
            e.termsDocumentId);
    return Offer.restore(
        new Offer.State(
            new OfferId(e.id),
            new TenantId(e.tenantId),
            terms,
            e.rules.stream().map(RuleCodec::decode).toList(),
            e.ruleSetVersion,
            e.status,
            new UserId(e.createdBy),
            e.createdAt,
            e.updatedAt,
            e.version));
  }

  static void apply(Offer offer, OfferEntity e) {
    OfferTerms terms = offer.terms();
    Reward reward = terms.reward();
    e.id = offer.id().value();
    e.tenantId = offer.tenantId().value();
    e.merchantId = terms.merchantId();
    e.title = terms.title();
    e.description = terms.description();
    e.category = terms.category();
    e.rewardType = reward.type();
    e.rewardPercent = reward.percent();
    e.rewardFixed = reward.fixedAmount() == null ? null : reward.fixedAmount().amount();
    e.maxReward = reward.maxReward().amount();
    e.minSpend = terms.minSpend().amount();
    e.currency = terms.totalBudget().currency().getCurrencyCode();
    e.perCustomerCap = terms.perCustomerCap();
    e.totalBudget = terms.totalBudget().amount();
    e.startAt = terms.validity().startAt();
    e.endAt = terms.validity().endAt();
    e.status = offer.status();
    e.termsDocumentId = terms.termsDocumentId();
    e.ruleSetVersion = offer.ruleSetVersion();
    e.createdBy = offer.createdBy().value();
    e.createdAt = offer.createdAt();
    e.updatedAt = offer.updatedAt();
    e.rules.clear();
    offer.rules().stream().map(RuleCodec::encode).forEach(e.rules::add);
  }
}
