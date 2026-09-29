package com.vasmarketing.offer.application;

import com.vasmarketing.offer.domain.model.OfferCategory;
import com.vasmarketing.offer.domain.model.OfferStatus;
import java.time.Instant;
import java.util.UUID;

/** List view of an offer (no rules). */
public record OfferSummary(
    UUID id,
    UUID merchantId,
    String title,
    OfferCategory category,
    OfferStatus status,
    Instant startAt,
    Instant endAt,
    long version) {}
