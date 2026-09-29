package com.vasmarketing.campaign.domain.model;

import java.util.Objects;

/** Provenance of AI-generated content: which prompt version and model produced it. */
public record PromptReference(String promptId, String promptVersion, String modelId) {

  public PromptReference {
    Objects.requireNonNull(promptId, "promptId");
    Objects.requireNonNull(promptVersion, "promptVersion");
    Objects.requireNonNull(modelId, "modelId");
  }
}
