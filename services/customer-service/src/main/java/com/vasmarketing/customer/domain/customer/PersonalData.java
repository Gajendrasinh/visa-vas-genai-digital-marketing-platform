package com.vasmarketing.customer.domain.customer;

import com.vasmarketing.platform.types.DomainRuleViolation;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Directly identifying data (PII). Never logged, never published on Kafka, never sent to an LLM;
 * encrypted at rest by the persistence adapter. {@link #toString()} is masked on purpose.
 *
 * @param phone E.164 format, optional
 */
public record PersonalData(String fullName, String email, String phone) {

  private static final Pattern EMAIL =
      Pattern.compile("^[A-Za-z0-9._%+-]{1,64}@[A-Za-z0-9.-]{1,185}\\.[A-Za-z]{2,63}$");
  private static final Pattern E164 = Pattern.compile("^\\+[1-9][0-9]{7,14}$");
  private static final int MAX_NAME = 200;

  public PersonalData {
    if (fullName == null || fullName.isBlank() || fullName.length() > MAX_NAME) {
      throw invalid("full name must be 1-" + MAX_NAME + " characters");
    }
    if (email == null || !EMAIL.matcher(email).matches()) {
      throw invalid("email is not valid");
    }
    email = email.toLowerCase(Locale.ROOT);
    if (phone != null && !E164.matcher(phone).matches()) {
      throw invalid("phone must be in E.164 format");
    }
  }

  public Optional<String> phoneNumber() {
    return Optional.ofNullable(phone);
  }

  /** A display-safe view for roles without PII access. */
  public MaskedPersonalData masked() {
    return new MaskedPersonalData(maskName(fullName), maskEmail(email), maskPhone(phone));
  }

  @Override
  public String toString() {
    return "PersonalData" + masked();
  }

  private static String maskName(String name) {
    StringBuilder masked = new StringBuilder();
    for (String part : name.trim().split("\\s+")) {
      if (!masked.isEmpty()) {
        masked.append(' ');
      }
      masked.append(part.charAt(0)).append("***");
    }
    return masked.toString();
  }

  private static String maskEmail(String address) {
    int at = address.indexOf('@');
    return address.charAt(0) + "***" + address.substring(at);
  }

  private static String maskPhone(String number) {
    return number == null ? null : "+***" + number.substring(number.length() - 4);
  }

  private static DomainRuleViolation invalid(String message) {
    return new DomainRuleViolation(CustomerErrors.INVALID_PERSONAL_DATA, message);
  }

  /** Masked personal data, e.g. {@code J*** D***}, {@code j***@example.com}. */
  public record MaskedPersonalData(String fullName, String email, String phone) {}
}
