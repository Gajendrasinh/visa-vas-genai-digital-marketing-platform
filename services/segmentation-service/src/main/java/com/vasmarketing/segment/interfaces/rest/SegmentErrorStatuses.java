package com.vasmarketing.segment.interfaces.rest;

import static com.vasmarketing.segment.domain.criteria.SegmentErrorCodes.DUPLICATE_NAME;
import static com.vasmarketing.segment.domain.criteria.SegmentErrorCodes.INVALID_DEFINITION;
import static com.vasmarketing.segment.domain.criteria.SegmentErrorCodes.INVALID_DETAILS;
import static com.vasmarketing.segment.domain.criteria.SegmentErrorCodes.INVALID_TRANSITION;
import static com.vasmarketing.segment.domain.criteria.SegmentErrorCodes.NOT_EDITABLE;

import com.vasmarketing.platform.web.error.ErrorStatusMapping;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
class SegmentErrorStatuses implements ErrorStatusMapping {

  @Override
  public Map<String, HttpStatus> statuses() {
    return Map.of(
        INVALID_DEFINITION, HttpStatus.BAD_REQUEST,
        INVALID_DETAILS, HttpStatus.BAD_REQUEST,
        NOT_EDITABLE, HttpStatus.CONFLICT,
        INVALID_TRANSITION, HttpStatus.CONFLICT,
        DUPLICATE_NAME, HttpStatus.CONFLICT);
  }
}
