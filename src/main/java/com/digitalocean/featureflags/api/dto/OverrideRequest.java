package com.digitalocean.featureflags.api.dto;

import com.digitalocean.featureflags.domain.Variation;
import jakarta.validation.constraints.NotNull;

public record OverrideRequest(@NotNull Variation state) {
}
