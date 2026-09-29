package com.vasmarketing.campaign.interfaces.rest;

import com.vasmarketing.campaign.domain.model.ApprovalDecision;
import com.vasmarketing.campaign.domain.model.CampaignDetails;
import com.vasmarketing.campaign.domain.model.Channel;
import com.vasmarketing.campaign.domain.model.ContentAuthor;
import com.vasmarketing.campaign.domain.model.PromptReference;
import com.vasmarketing.campaign.domain.model.Schedule;
import com.vasmarketing.campaign.domain.model.VariantContent;
import com.vasmarketing.platform.types.Money;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.Set;
import java.util.UUID;

/** Request bodies. Bean Validation gives field-level 400s; the domain re-validates every rule. */
final class CampaignRequests {

  private CampaignRequests() {}

  @Schema(description = "Money as a decimal string to avoid binary floating point")
  record MoneyDto(
      @NotNull @Pattern(regexp = "^\\d{1,15}(\\.\\d{1,4})?$") String amount,
      @NotNull @Pattern(regexp = "^[A-Z]{3}$") String currency) {

    Money toMoney() {
      return new Money(new BigDecimal(amount), Currency.getInstance(currency));
    }
  }

  record CampaignDetailsRequest(
      @NotBlank @Size(max = 120) String name,
      @NotBlank @Size(max = 500) String objective,
      @NotNull UUID segmentId,
      @NotNull UUID offerId,
      @NotNull Instant startAt,
      @NotNull Instant endAt,
      @NotNull @Valid MoneyDto budget,
      @NotEmpty Set<@NotNull Channel> channels) {

    CampaignDetails toDetails() {
      return new CampaignDetails(
          name,
          objective,
          segmentId,
          offerId,
          new Schedule(startAt, endAt),
          budget.toMoney(),
          channels);
    }
  }

  record PromptReferenceDto(
      @NotBlank @Size(max = 100) String promptId,
      @NotBlank @Size(max = 20) String promptVersion,
      @NotBlank @Size(max = 100) String modelId) {}

  record AddVariantRequest(
      @NotNull Channel channel,
      @NotBlank @Size(max = 32) String variantKey,
      @NotBlank @Size(max = 5) String language,
      @Size(max = 150) String subject,
      @NotBlank @Size(max = 5000) String bodyTemplate,
      @NotNull ContentAuthor generatedBy,
      @Valid PromptReferenceDto prompt) {

    VariantContent toContent() {
      return new VariantContent(channel, variantKey, language, subject, bodyTemplate);
    }

    PromptReference toPromptReference() {
      return prompt == null
          ? null
          : new PromptReference(prompt.promptId(), prompt.promptVersion(), prompt.modelId());
    }
  }

  record ComplianceReviewRequest(
      @NotNull Boolean passed, @NotBlank @Size(max = 10_000) String report) {}

  record ApprovalRequest(@NotNull ApprovalDecision decision, @Size(max = 1000) String comment) {}
}
