package com.vasmarketing.campaign.domain.model;

import java.util.Objects;
import java.util.Optional;

/** A piece of channel content belonging to a campaign. Mutated only through {@link Campaign}. */
public final class ContentVariant {

  private final VariantId id;
  private final VariantContent content;
  private final ContentAuthor author;
  private final PromptReference promptReference;
  private VariantStatus status;
  private String complianceReport;

  ContentVariant(
      VariantId id,
      VariantContent content,
      ContentAuthor author,
      PromptReference promptReference,
      VariantStatus status,
      String complianceReport) {
    this.id = Objects.requireNonNull(id, "id");
    this.content = Objects.requireNonNull(content, "content");
    this.author = Objects.requireNonNull(author, "author");
    this.status = Objects.requireNonNull(status, "status");
    if ((author == ContentAuthor.AI) != (promptReference != null)) {
      throw new IllegalArgumentException(
          "AI content requires a prompt reference, human content none");
    }
    this.promptReference = promptReference;
    this.complianceReport = complianceReport;
  }

  /** Rehydrates a persisted variant. */
  public static ContentVariant restore(
      VariantId id,
      VariantContent content,
      ContentAuthor author,
      PromptReference promptReference,
      VariantStatus status,
      String complianceReport) {
    return new ContentVariant(id, content, author, promptReference, status, complianceReport);
  }

  void recordCompliance(boolean passed, String report) {
    status = passed ? VariantStatus.COMPLIANCE_PASSED : VariantStatus.COMPLIANCE_FAILED;
    complianceReport = report;
  }

  void approve() {
    if (status == VariantStatus.COMPLIANCE_PASSED) {
      status = VariantStatus.APPROVED;
    }
  }

  boolean sameSlotAs(VariantContent other) {
    return content.channel() == other.channel()
        && content.variantKey().equals(other.variantKey())
        && content.language().equals(other.language());
  }

  public VariantId id() {
    return id;
  }

  public VariantContent content() {
    return content;
  }

  public ContentAuthor author() {
    return author;
  }

  public Optional<PromptReference> promptReference() {
    return Optional.ofNullable(promptReference);
  }

  public VariantStatus status() {
    return status;
  }

  public Optional<String> complianceReport() {
    return Optional.ofNullable(complianceReport);
  }
}
