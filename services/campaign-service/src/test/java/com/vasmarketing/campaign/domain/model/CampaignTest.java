package com.vasmarketing.campaign.domain.model;

import static com.vasmarketing.campaign.CampaignFixtures.APPROVER;
import static com.vasmarketing.campaign.CampaignFixtures.CREATOR;
import static com.vasmarketing.campaign.CampaignFixtures.NOW;
import static com.vasmarketing.campaign.CampaignFixtures.details;
import static com.vasmarketing.campaign.CampaignFixtures.email;
import static com.vasmarketing.campaign.CampaignFixtures.push;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vasmarketing.campaign.domain.event.CampaignEvent;
import com.vasmarketing.platform.types.DomainRuleViolation;
import com.vasmarketing.platform.types.UserId;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CampaignTest {

  private static Campaign draftWithCompliantEmail() {
    Campaign campaign =
        Campaign.create(
            CREATOR, details("Travel Q4", Channel.EMAIL), CampaignOrigin.MANUAL, null, NOW);
    VariantId variant = campaign.addVariant(email("A"), ContentAuthor.HUMAN, null, NOW);
    campaign.recordCompliance(variant, true, "rule pack v1: pass", NOW);
    return campaign;
  }

  private static Campaign approved() {
    Campaign campaign = draftWithCompliantEmail();
    campaign.submit(CREATOR, NOW);
    campaign.approve(APPROVER, "looks good", NOW);
    return campaign;
  }

  @Test
  void newCampaignStartsAsDraftAndRecordsCreation() {
    Campaign campaign =
        Campaign.create(
            CREATOR, details("Travel", Channel.EMAIL), CampaignOrigin.MANUAL, null, NOW);

    assertThat(campaign.status()).isEqualTo(CampaignStatus.DRAFT);
    assertThat(campaign.createdBy()).isEqualTo(CREATOR.userId());
    assertThat(campaign.version()).isEmpty();
    assertThat(campaign.pullEvents())
        .singleElement()
        .isInstanceOf(CampaignEvent.CampaignCreated.class);
    assertThat(campaign.pullEvents()).isEmpty();
  }

  @Test
  void aiAssistedCampaignMustReferenceItsAiRequest() {
    assertThatThrownBy(
            () ->
                Campaign.create(
                    CREATOR, details("AI", Channel.EMAIL), CampaignOrigin.AI_ASSISTED, null, NOW))
        .isInstanceOf(IllegalArgumentException.class);

    Campaign campaign =
        Campaign.create(
            CREATOR,
            details("AI", Channel.EMAIL),
            CampaignOrigin.AI_ASSISTED,
            UUID.randomUUID(),
            NOW);
    assertThat(campaign.status()).isEqualTo(CampaignStatus.DRAFT);
  }

  @Test
  void fullLifecycleFromDraftToArchived() {
    Campaign campaign = approved();
    campaign.publish(CREATOR, NOW);
    assertThat(campaign.status()).isEqualTo(CampaignStatus.SCHEDULED);

    Instant started = campaign.details().schedule().startAt();
    campaign.activate(new UserId("scheduler"), started);
    campaign.pause(CREATOR, started.plusSeconds(60));
    campaign.resume(CREATOR, started.plusSeconds(120));
    campaign.complete(new UserId("scheduler"), campaign.details().schedule().endAt());
    campaign.archive(CREATOR, campaign.details().schedule().endAt());

    assertThat(campaign.status()).isEqualTo(CampaignStatus.ARCHIVED);
    assertThat(campaign.pullEvents())
        .extracting(e -> e.getClass().getSimpleName())
        .containsExactly(
            "CampaignCreated",
            "CampaignSubmitted",
            "CampaignApproved",
            "CampaignPublished",
            "CampaignActivated",
            "CampaignPaused",
            "CampaignResumed",
            "CampaignCompleted",
            "CampaignArchived");
  }

  @Test
  void creatorCannotApproveOwnCampaign() {
    Campaign campaign = draftWithCompliantEmail();
    campaign.submit(CREATOR, NOW);

    assertThatThrownBy(() -> campaign.approve(CREATOR, "self", NOW))
        .isInstanceOf(DomainRuleViolation.class)
        .extracting("code")
        .isEqualTo(CampaignErrors.SELF_APPROVAL);
    assertThat(campaign.status()).isEqualTo(CampaignStatus.PENDING_APPROVAL);
  }

  @Test
  void approvalPromotesOnlyCompliantVariants() {
    Campaign campaign =
        Campaign.create(
            CREATOR,
            details("Mixed", Channel.EMAIL, Channel.PUSH),
            CampaignOrigin.MANUAL,
            null,
            NOW);
    VariantId passedEmail = campaign.addVariant(email("A"), ContentAuthor.HUMAN, null, NOW);
    VariantId failedEmail = campaign.addVariant(email("B"), ContentAuthor.HUMAN, null, NOW);
    VariantId passedPush = campaign.addVariant(push("A"), ContentAuthor.HUMAN, null, NOW);
    campaign.recordCompliance(passedEmail, true, "pass", NOW);
    campaign.recordCompliance(failedEmail, false, "prohibited claim: guaranteed", NOW);
    campaign.recordCompliance(passedPush, true, "pass", NOW);

    campaign.submit(CREATOR, NOW);
    campaign.approve(APPROVER, null, NOW);

    assertThat(campaign.variants())
        .extracting(ContentVariant::status)
        .containsExactlyInAnyOrder(
            VariantStatus.APPROVED, VariantStatus.COMPLIANCE_FAILED, VariantStatus.APPROVED);
    assertThat(campaign.approvals())
        .singleElement()
        .extracting(Approval::decidedBy)
        .isEqualTo(APPROVER.userId());
  }

  @Test
  void submitRequiresACompliantVariantForEveryChannel() {
    Campaign campaign =
        Campaign.create(
            CREATOR,
            details("Two channels", Channel.EMAIL, Channel.PUSH),
            CampaignOrigin.MANUAL,
            null,
            NOW);
    VariantId emailVariant = campaign.addVariant(email("A"), ContentAuthor.HUMAN, null, NOW);
    campaign.recordCompliance(emailVariant, true, "pass", NOW);

    assertThatThrownBy(() -> campaign.submit(CREATOR, NOW))
        .isInstanceOf(DomainRuleViolation.class)
        .hasMessageContaining("PUSH")
        .extracting("code")
        .isEqualTo(CampaignErrors.MISSING_APPROVABLE_VARIANT);
  }

  @Test
  void contentIsEditableOnlyInDraft() {
    Campaign campaign = draftWithCompliantEmail();
    campaign.submit(CREATOR, NOW);

    assertThatThrownBy(() -> campaign.addVariant(email("C"), ContentAuthor.HUMAN, null, NOW))
        .extracting("code")
        .isEqualTo(CampaignErrors.NOT_EDITABLE);
    assertThatThrownBy(() -> campaign.updateDetails(details("Renamed", Channel.EMAIL), NOW))
        .extracting("code")
        .isEqualTo(CampaignErrors.NOT_EDITABLE);
  }

  @Test
  void rejectionReturnsToDraftAndRequiresReason() {
    Campaign campaign = draftWithCompliantEmail();
    campaign.submit(CREATOR, NOW);

    assertThatThrownBy(() -> campaign.reject(APPROVER, " ", NOW))
        .extracting("code")
        .isEqualTo(CampaignErrors.INVALID_DETAILS);

    campaign.reject(APPROVER, "tone too aggressive", NOW);
    assertThat(campaign.status()).isEqualTo(CampaignStatus.DRAFT);
    assertThat(campaign.approvals())
        .singleElement()
        .extracting(Approval::decision)
        .isEqualTo(ApprovalDecision.REJECT);
  }

  @Test
  void publishActivatesImmediatelyWhenStartHasPassed() {
    Campaign campaign = approved();
    Instant afterStart = campaign.details().schedule().startAt().plusSeconds(1);

    campaign.publish(APPROVER, afterStart);

    assertThat(campaign.status()).isEqualTo(CampaignStatus.ACTIVE);
    assertThat(campaign.publishedBy()).contains(APPROVER.userId());
    assertThat(campaign.publishedAt()).contains(afterStart);
  }

  @Test
  void cannotPublishOrResumeAfterEndDate() {
    Campaign campaign = approved();
    Instant end = campaign.details().schedule().endAt();

    assertThatThrownBy(() -> campaign.publish(CREATOR, end))
        .extracting("code")
        .isEqualTo(CampaignErrors.SCHEDULE_ELAPSED);

    campaign.publish(CREATOR, end.minusSeconds(10));
    campaign.pause(CREATOR, end.minusSeconds(5));
    assertThatThrownBy(() -> campaign.resume(CREATOR, end))
        .extracting("code")
        .isEqualTo(CampaignErrors.SCHEDULE_ELAPSED);
  }

  @Test
  void schedulerCannotActivateBeforeStart() {
    Campaign campaign = approved();
    campaign.publish(CREATOR, NOW);

    assertThatThrownBy(() -> campaign.activate(new UserId("scheduler"), NOW))
        .extracting("code")
        .isEqualTo(CampaignErrors.INVALID_TRANSITION);
  }

  @Test
  void invalidTransitionsAreRejected() {
    Campaign campaign = draftWithCompliantEmail();

    assertThatThrownBy(() -> campaign.publish(CREATOR, NOW))
        .extracting("code")
        .isEqualTo(CampaignErrors.INVALID_TRANSITION);
    assertThatThrownBy(() -> campaign.pause(CREATOR, NOW))
        .extracting("code")
        .isEqualTo(CampaignErrors.INVALID_TRANSITION);
  }

  @Test
  void variantsMustMatchCampaignChannelsAndBeUnique() {
    Campaign campaign =
        Campaign.create(
            CREATOR, details("Email only", Channel.EMAIL), CampaignOrigin.MANUAL, null, NOW);

    assertThatThrownBy(() -> campaign.addVariant(push("A"), ContentAuthor.HUMAN, null, NOW))
        .extracting("code")
        .isEqualTo(CampaignErrors.INVALID_VARIANT);

    campaign.addVariant(email("A"), ContentAuthor.HUMAN, null, NOW);
    assertThatThrownBy(() -> campaign.addVariant(email("A"), ContentAuthor.HUMAN, null, NOW))
        .extracting("code")
        .isEqualTo(CampaignErrors.DUPLICATE_VARIANT);
  }

  @Test
  void channelWithVariantsCannotBeRemovedUntilVariantsAreRemoved() {
    Campaign campaign =
        Campaign.create(
            CREATOR,
            details("Both", Channel.EMAIL, Channel.PUSH),
            CampaignOrigin.MANUAL,
            null,
            NOW);
    VariantId pushVariant = campaign.addVariant(push("A"), ContentAuthor.HUMAN, null, NOW);

    assertThatThrownBy(() -> campaign.updateDetails(details("Both", Channel.EMAIL), NOW))
        .extracting("code")
        .isEqualTo(CampaignErrors.INVALID_DETAILS);

    campaign.removeVariant(pushVariant, NOW);
    campaign.updateDetails(details("Both", Channel.EMAIL), NOW);
    assertThat(campaign.details().channels()).containsExactly(Channel.EMAIL);
  }

  @Test
  void unknownVariantIsReported() {
    Campaign campaign = draftWithCompliantEmail();

    assertThatThrownBy(() -> campaign.recordCompliance(VariantId.newId(), true, "x", NOW))
        .extracting("code")
        .isEqualTo(CampaignErrors.VARIANT_NOT_FOUND);
  }

  @Test
  void aiContentRequiresPromptProvenance() {
    Campaign campaign =
        Campaign.create(
            CREATOR, details("AI content", Channel.EMAIL), CampaignOrigin.MANUAL, null, NOW);

    assertThatThrownBy(() -> campaign.addVariant(email("A"), ContentAuthor.AI, null, NOW))
        .isInstanceOf(IllegalArgumentException.class);

    PromptReference prompt = new PromptReference("campaign_generation", "v2", "claude-sonnet-5");
    VariantId id = campaign.addVariant(email("A"), ContentAuthor.AI, prompt, NOW);
    assertThat(campaign.variants())
        .singleElement()
        .satisfies(
            v -> {
              assertThat(v.id()).isEqualTo(id);
              assertThat(v.promptReference()).contains(prompt);
            });
  }
}
