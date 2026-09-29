package com.vasmarketing.offer.interfaces.rest;

import static com.vasmarketing.offer.domain.model.OfferErrors.CONSENT_RULE_REQUIRED;
import static com.vasmarketing.offer.domain.model.OfferErrors.DUPLICATE_TITLE;
import static com.vasmarketing.offer.domain.model.OfferErrors.INVALID_OFFER;
import static com.vasmarketing.offer.domain.model.OfferErrors.INVALID_REWARD;
import static com.vasmarketing.offer.domain.model.OfferErrors.INVALID_RULE;
import static com.vasmarketing.offer.domain.model.OfferErrors.INVALID_TRANSITION;
import static com.vasmarketing.offer.domain.model.OfferErrors.NOT_EDITABLE;
import static com.vasmarketing.offer.domain.model.OfferErrors.VALIDITY_ELAPSED;

import com.vasmarketing.platform.web.error.ErrorStatusMapping;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
class OfferErrorStatuses implements ErrorStatusMapping {

  @Override
  public Map<String, HttpStatus> statuses() {
    return Map.of(
        INVALID_OFFER, HttpStatus.BAD_REQUEST,
        INVALID_REWARD, HttpStatus.BAD_REQUEST,
        INVALID_RULE, HttpStatus.BAD_REQUEST,
        NOT_EDITABLE, HttpStatus.CONFLICT,
        INVALID_TRANSITION, HttpStatus.CONFLICT,
        CONSENT_RULE_REQUIRED, HttpStatus.CONFLICT,
        VALIDITY_ELAPSED, HttpStatus.CONFLICT,
        DUPLICATE_TITLE, HttpStatus.CONFLICT);
  }
}
