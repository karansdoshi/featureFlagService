package com.digitalocean.featureflags.api.dto;

import jakarta.validation.constraints.NotBlank;

/** User context for an evaluation. All fields are required (DESIGN.md validation contract). */
public record EvaluateRequest(
        @NotBlank String userId,
        @NotBlank String subscriptionTier,
        @NotBlank String region
) {
}
