package com.vasmarketing.offer.application;

import com.vasmarketing.offer.domain.eligibility.EligibilityRule;
import com.vasmarketing.offer.domain.model.Offer;
import com.vasmarketing.offer.domain.model.OfferCategory;
import com.vasmarketing.offer.domain.model.OfferErrors;
import com.vasmarketing.offer.domain.model.OfferId;
import com.vasmarketing.offer.domain.model.OfferStatus;
import com.vasmarketing.offer.domain.model.OfferTerms;
import com.vasmarketing.offer.domain.port.OfferRepository;
import com.vasmarketing.platform.types.Actor;
import com.vasmarketing.platform.types.CursorPage;
import com.vasmarketing.platform.types.DomainRuleViolation;
import com.vasmarketing.platform.types.Precondition;
import com.vasmarketing.platform.types.ResourceNotFound;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Use cases for managing the offer catalogue. */
@Service
@Transactional
public class OfferManagementService {

  private final OfferRepository repository;
  private final OfferSummaries summaries;
  private final Clock clock;

  OfferManagementService(OfferRepository repository, OfferSummaries summaries, Clock clock) {
    this.repository = repository;
    this.summaries = summaries;
    this.clock = clock;
  }

  public Offer create(Actor actor, OfferTerms terms, List<EligibilityRule> rules) {
    requireUniqueTitle(actor, terms.title());
    return repository.save(Offer.create(actor, terms, rules, clock.instant()));
  }

  public Offer updateTerms(Actor actor, OfferId id, OfferTerms terms, Precondition precondition) {
    Offer offer = loadForUpdate(actor, id, precondition);
    if (!offer.terms().title().equals(terms.title())) {
      requireUniqueTitle(actor, terms.title());
    }
    offer.updateTerms(terms, clock.instant());
    return repository.save(offer);
  }

  public Offer replaceRules(
      Actor actor, OfferId id, List<EligibilityRule> rules, Precondition precondition) {
    return apply(actor, id, precondition, (offer, now) -> offer.replaceRules(rules, now));
  }

  public Offer activate(Actor actor, OfferId id, Precondition precondition) {
    return apply(actor, id, precondition, (offer, now) -> offer.activate(now));
  }

  public Offer pause(Actor actor, OfferId id, Precondition precondition) {
    return apply(actor, id, precondition, (offer, now) -> offer.pause(now));
  }

  /** Newest offers first; filters are optional. */
  @Transactional(readOnly = true)
  public CursorPage<OfferSummary> list(
      Actor actor, OfferStatus status, OfferCategory category, String cursor, int limit) {
    int size = CursorPage.limit(limit);
    UUID before = cursor == null ? null : CursorPage.decode(cursor);
    return CursorPage.fromOverfetch(
        summaries.page(actor.tenantId(), status, category, before, size + 1),
        size,
        OfferSummary::id);
  }

  @Transactional(readOnly = true)
  public Offer get(Actor actor, OfferId id) {
    return load(actor, id);
  }

  private Offer apply(
      Actor actor, OfferId id, Precondition precondition, BiConsumer<Offer, Instant> change) {
    Offer offer = loadForUpdate(actor, id, precondition);
    change.accept(offer, clock.instant());
    return repository.save(offer);
  }

  private Offer load(Actor actor, OfferId id) {
    return repository
        .findById(actor.tenantId(), id)
        .orElseThrow(() -> new ResourceNotFound("Offer", id));
  }

  private Offer loadForUpdate(Actor actor, OfferId id, Precondition precondition) {
    Offer offer = load(actor, id);
    precondition.check(offer.version());
    return offer;
  }

  private void requireUniqueTitle(Actor actor, String title) {
    if (repository.existsByTitle(actor.tenantId(), title)) {
      throw new DomainRuleViolation(
          OfferErrors.DUPLICATE_TITLE, "an offer titled '" + title + "' already exists");
    }
  }
}
