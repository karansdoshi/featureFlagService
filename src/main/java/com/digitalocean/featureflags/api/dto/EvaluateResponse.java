package com.digitalocean.featureflags.api.dto;

import com.digitalocean.featureflags.domain.Reason;

/** Evaluation result. {@code reason} is always present. */
public record EvaluateResponse(String flag, boolean enabled, Reason reason) {
}
