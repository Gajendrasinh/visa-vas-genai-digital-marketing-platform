package com.vasmarketing.customer.application;

import com.vasmarketing.customer.domain.customer.Customer;
import com.vasmarketing.customer.domain.customer.CustomerErrors;
import com.vasmarketing.customer.domain.customer.CustomerId;
import com.vasmarketing.customer.domain.customer.CustomerRepository;
import com.vasmarketing.customer.domain.customer.PersonalData;
import com.vasmarketing.customer.domain.customer.Preferences;
import com.vasmarketing.platform.types.Actor;
import com.vasmarketing.platform.types.DomainRuleViolation;
import com.vasmarketing.platform.types.Precondition;
import com.vasmarketing.platform.types.ResourceNotFound;
import java.time.Clock;
import java.time.Instant;
import java.util.function.BiConsumer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Use cases for the customer record, consent and preferences. */
@Service
@Transactional
public class CustomerService {

  private final CustomerRepository repository;
  private final Clock clock;

  CustomerService(CustomerRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  public Customer register(Actor actor, RegisterCustomerCommand command) {
    if (repository.findByEmail(actor.tenantId(), command.personalData().email()).isPresent()) {
      throw new DomainRuleViolation(
          CustomerErrors.DUPLICATE_EMAIL, "a customer with this email already exists");
    }
    return repository.save(
        Customer.register(
            actor.tenantId(),
            command.personalData(),
            command.countryCode(),
            command.birthYear(),
            command.preferences(),
            clock.instant()));
  }

  public Customer updateContact(
      Actor actor, CustomerId id, PersonalData personalData, Precondition precondition) {
    return apply(
        actor, id, precondition, (customer, now) -> customer.updateContact(personalData, now));
  }

  public Customer updatePreferences(
      Actor actor, CustomerId id, Preferences preferences, Precondition precondition) {
    return apply(
        actor, id, precondition, (customer, now) -> customer.updatePreferences(preferences, now));
  }

  public Customer grantMarketingConsent(Actor actor, CustomerId id, Precondition precondition) {
    return apply(actor, id, precondition, (customer, now) -> customer.grantMarketingConsent(now));
  }

  public Customer withdrawMarketingConsent(Actor actor, CustomerId id, Precondition precondition) {
    return apply(
        actor, id, precondition, (customer, now) -> customer.withdrawMarketingConsent(now));
  }

  public Customer close(Actor actor, CustomerId id, Precondition precondition) {
    return apply(actor, id, precondition, (customer, now) -> customer.close(now));
  }

  @Transactional(readOnly = true)
  public Customer get(Actor actor, CustomerId id) {
    return load(actor, id);
  }

  private Customer apply(
      Actor actor, CustomerId id, Precondition precondition, BiConsumer<Customer, Instant> change) {
    Customer customer = load(actor, id);
    precondition.check(customer.version());
    change.accept(customer, clock.instant());
    return repository.save(customer);
  }

  private Customer load(Actor actor, CustomerId id) {
    return repository
        .findById(actor.tenantId(), id)
        .orElseThrow(() -> new ResourceNotFound("Customer", id));
  }
}
