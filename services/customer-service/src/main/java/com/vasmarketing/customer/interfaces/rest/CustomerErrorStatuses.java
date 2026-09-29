package com.vasmarketing.customer.interfaces.rest;

import com.vasmarketing.customer.domain.customer.CustomerErrors;
import com.vasmarketing.customer.domain.merchant.MerchantErrors;
import com.vasmarketing.platform.web.error.ErrorStatusMapping;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
class CustomerErrorStatuses implements ErrorStatusMapping {

  @Override
  public Map<String, HttpStatus> statuses() {
    return Map.of(
        CustomerErrors.INVALID_PERSONAL_DATA, HttpStatus.BAD_REQUEST,
        CustomerErrors.INVALID_PROFILE, HttpStatus.BAD_REQUEST,
        CustomerErrors.DUPLICATE_EMAIL, HttpStatus.CONFLICT,
        CustomerErrors.CLOSED, HttpStatus.CONFLICT,
        MerchantErrors.INVALID_MERCHANT, HttpStatus.BAD_REQUEST,
        MerchantErrors.INVALID_TRANSITION, HttpStatus.CONFLICT);
  }
}
