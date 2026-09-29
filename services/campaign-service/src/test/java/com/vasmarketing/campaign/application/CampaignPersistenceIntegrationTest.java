package com.vasmarketing.campaign.application;

import static com.vasmarketing.campaign.CampaignFixtures.APPROVER;
import static com.vasmarketing.campaign.CampaignFixtures.CREATOR;
import static com.vasmarketing.campaign.CampaignFixtures.details;
import static com.vasmarketing.campaign.CampaignFixtures.email;
import static com.vasmarketing.campaign.CampaignFixtures.push;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vasmarketing.campaign.domain.model.Campaign;
import com.vasmarketing.campaign.domain.model.CampaignErrors;
import com.vasmarketing.campaign.domain.model.CampaignStatus;
import com.vasmarketing.campaign.domain.model.Channel;
import com.vasmarketing.campaign.domain.model.ContentAuthor;
import com.vasmarketing.campaign.domain.model.ContentVariant;
import com.vasmarketing.campaign.domain.model.PromptReference;
import com.vasmarketing.campaign.domain.model.VariantId;
import com.vasmarketing.campaign.domain.model.VariantStatus;
import com.vasmarketing.campaign.domain.port.CampaignRepository;
import com.vasmarketing.platform.testsupport.ServicePostgres;
import com.vasmarketing.platform.types.Actor;
import com.vasmarketing.platform.types.DomainRuleViolation;
import com.vasmarketing.platform.types.ResourceNotFound;
import com.vasmarketing.platform.types.TenantId;
import com.vasmarketing.platform.types.UserId;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Runs the application services against a real Postgres with Flyway migrations applied by the
 * least-privileged service role, as in production.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CampaignPersistenceIntegrationTest {

  private static final AtomicInteger NAMES = new AtomicInteger();

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    ServicePostgres.register(registry, "campaign");
  }

  @Autowired private CampaignAuthoringService authoring;
  @Autowired private CampaignLifecycleService lifecycle;
  @Autowired private CampaignQueryService queries;
  @Autowired private CampaignRepository repository;

  private static String uniqueName() {
    return "Travel " + NAMES.incrementAndGet();
  }

  @Test
  void persistsTheFullLifecycleIncludingVariantsAndApprovals() {
    Campaign created =
        authoring.create(
            CREATOR,
            CreateCampaignCommand.manual(details(uniqueName(), Channel.EMAIL, Channel.PUSH)));
    PromptReference prompt = new PromptReference("campaign_generation", "v2", "claude-sonnet-5");
    VariantId emailVariant =
        authoring.addVariant(CREATOR, created.id(), email("A"), ContentAuthor.AI, prompt);
    VariantId pushVariant =
        authoring.addVariant(CREATOR, created.id(), push("A"), ContentAuthor.HUMAN, null);
    authoring.recordCompliance(CREATOR, created.id(), emailVariant, true, "rule pack v1: pass");
    authoring.recordCompliance(CREATOR, created.id(), pushVariant, true, "rule pack v1: pass");

    lifecycle.submit(CREATOR, created.id());
    lifecycle.approve(APPROVER, created.id(), "approved for Q4");
    lifecycle.publish(APPROVER, created.id());

    Campaign reloaded = queries.get(CREATOR, created.id());
    assertThat(reloaded.status()).isEqualTo(CampaignStatus.SCHEDULED);
    assertThat(reloaded.details()).isEqualTo(created.details());
    assertThat(reloaded.publishedBy()).contains(APPROVER.userId());
    assertThat(reloaded.approvals())
        .singleElement()
        .satisfies(a -> assertThat(a.comment()).isEqualTo("approved for Q4"));
    assertThat(reloaded.variants())
        .extracting(ContentVariant::status)
        .containsOnly(VariantStatus.APPROVED);
    assertThat(reloaded.variants())
        .filteredOn(v -> v.id().equals(emailVariant))
        .singleElement()
        .satisfies(v -> assertThat(v.promptReference()).contains(prompt));
    assertThat(reloaded.version()).isPresent();
  }

  @Test
  void campaignsAreInvisibleToOtherTenants() {
    Campaign created =
        authoring.create(
            CREATOR, CreateCampaignCommand.manual(details(uniqueName(), Channel.EMAIL)));
    Actor otherTenant = new Actor(new UserId("manager"), new TenantId(UUID.randomUUID()));

    assertThatThrownBy(() -> queries.get(otherTenant, created.id()))
        .isInstanceOf(ResourceNotFound.class);
  }

  @Test
  void campaignNamesAreUniquePerTenant() {
    String name = uniqueName();
    authoring.create(CREATOR, CreateCampaignCommand.manual(details(name, Channel.EMAIL)));

    assertThatThrownBy(
            () ->
                authoring.create(
                    CREATOR, CreateCampaignCommand.manual(details(name, Channel.EMAIL))))
        .isInstanceOf(DomainRuleViolation.class)
        .extracting("code")
        .isEqualTo(CampaignErrors.DUPLICATE_NAME);

    Actor otherTenant = new Actor(new UserId("manager"), new TenantId(UUID.randomUUID()));
    assertThat(
            authoring
                .create(otherTenant, CreateCampaignCommand.manual(details(name, Channel.EMAIL)))
                .id())
        .isNotNull();
  }

  @Test
  void concurrentModificationIsDetected() {
    Campaign created =
        authoring.create(
            CREATOR, CreateCampaignCommand.manual(details(uniqueName(), Channel.EMAIL)));
    Campaign first = repository.findById(CREATOR.tenantId(), created.id()).orElseThrow();
    Campaign second = repository.findById(CREATOR.tenantId(), created.id()).orElseThrow();

    first.addVariant(email("A"), ContentAuthor.HUMAN, null, created.createdAt());
    repository.save(first);
    second.addVariant(email("B"), ContentAuthor.HUMAN, null, created.createdAt());

    assertThatThrownBy(() -> repository.save(second))
        .isInstanceOf(ObjectOptimisticLockingFailureException.class);
  }

  @Test
  void removedVariantsAreDeleted() {
    Campaign created =
        authoring.create(
            CREATOR, CreateCampaignCommand.manual(details(uniqueName(), Channel.EMAIL)));
    VariantId variant =
        authoring.addVariant(CREATOR, created.id(), email("A"), ContentAuthor.HUMAN, null);

    Campaign afterRemoval = authoring.removeVariant(CREATOR, created.id(), variant);

    assertThat(afterRemoval.variants()).isEmpty();
    assertThat(queries.get(CREATOR, created.id()).variants()).isEmpty();
  }

  @Test
  void databaseRejectsSelfApprovalEvenWhenDomainIsBypassed() throws SQLException {
    Campaign created =
        authoring.create(
            CREATOR, CreateCampaignCommand.manual(details(uniqueName(), Channel.EMAIL)));

    try (Connection connection = ServicePostgres.serviceConnection("campaign");
        PreparedStatement insert =
            connection.prepareStatement(
                "insert into campaign_approvals (id, campaign_id, decision, decided_by, decided_at)"
                    + " values (?, ?, 'APPROVE', ?, now())")) {
      insert.setObject(1, UUID.randomUUID());
      insert.setObject(2, created.id().value());
      insert.setString(3, CREATOR.userId().value());

      assertThatThrownBy(insert::executeUpdate)
          .isInstanceOf(SQLException.class)
          .hasMessageContaining("cannot be approved by its creator");
    }
  }

  @Test
  void serviceRoleCannotReachOtherSchemas() throws SQLException {
    try (Connection connection = ServicePostgres.serviceConnection("campaign");
        PreparedStatement create =
            connection.prepareStatement("create table public.probe (id int)")) {
      assertThatThrownBy(create::execute).hasMessageContaining("permission denied");
    }
  }
}
