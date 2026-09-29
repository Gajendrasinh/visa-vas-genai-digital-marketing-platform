package com.vasmarketing.customer.application;

import com.vasmarketing.customer.domain.customer.PersonalData;
import com.vasmarketing.customer.domain.customer.Preferences;
import java.util.Objects;

public record RegisterCustomerCommand(
    PersonalData personalData, String countryCode, int birthYear, Preferences preferences) {

  public RegisterCustomerCommand {
    Objects.requireNonNull(personalData, "personalData");
    Objects.requireNonNull(preferences, "preferences");
  }
}
