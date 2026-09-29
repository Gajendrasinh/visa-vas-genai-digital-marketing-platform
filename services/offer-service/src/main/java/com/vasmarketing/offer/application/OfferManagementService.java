package com.vasmarketing.offer.application;

import com.vasmarketing.offer.domain.eligibility.EligibilityRule;
import com.vasmarketing.offer.domain.model.Offer;
import com.vasmarketing.offer.domain.model.OfferErrors;
import com.vasmarketing.offer.domain.model.OfferId;
import com.vasmarketing.offer.domain.model.OfferTerms;
import com.vasmarketing.offer.domain.port.OfferRepository;
import com.vasmarketing.platform.types.Actor;
import com.vasmarketing.platform.types.DomainRuleViolation;
import com.vasmarketing.platform.types.ResourceNotFound;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.function.BiConsumer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Use cases for managing the offer catalogue. */
@Service
@Transactional
public class OfferManagementService {

  private final OfferRepository repository;
  private final Clock clock;

  OfferManagementService(OfferRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  public Offer create(Actor actor, OfferTerms terms, List<EligibilityRule> rules) {
    requireUniqueTitle(actor, terms.title());
    return repository.save(Offer.create(actor, terms, rules, clock.instant()));
  }

  public Offer updateTerms(Actor actor, OfferId id, OfferTerms terms) {
    Offer offer = load(actor, id);
    if (!offer.terms().title().equals(terms.title())) {
      requireUniqueTitle(actor, terms.title());
    }
    offer.updateTerms(terms, clock.instant());
    return repository.save(offer);
  }

  public Offer replaceRules(Actor actor, OfferId id, List<EligibilityRule> rules) {
    return apply(actor, id, (offer, now) -> offer.replaceRules(rules, now));
  }

  public Offer activate(Actor actor, OfferId id) {
    return apply(actor, id, (offer, now) -> offer.activate(now));
  }

  public Offer pause(Actor actor, OfferId id) {
    return apply(actor, id, (offer, now) -> offer.pause(now));
  }

  @Transactional(readOnly = true)
  public Offer get(Actor actor, OfferId id) {
    return load(actor, id);
  }

  private Offer apply(Actor actor, OfferId id, BiConsumer<Offer, Instant> change) {
    Offer offer = load(actor, id);
    change.accept(offer, clock.instant());
    return repository.save(offer);
  }

  private Offer load(Actor actor, OfferId id) {
    return repository
        .findById(actor.tenantId(), id)
        .orElseThrow(() -> new ResourceNotFound("Offer", id));
  }

  private void requireUniqueTitle(Actor actor, String title) {
    if (repository.existsByTitle(actor.tenantId(), title)) {
      throw new DomainRuleViolation(
          OfferErrors.DUPLICATE_TITLE, "an offer titled '" + title + "' already exists");
    }
  }
}
