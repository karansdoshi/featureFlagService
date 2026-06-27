package com.digitalocean.featureflags.api.dto;

import com.digitalocean.featureflags.domain.Variation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** Update payload; the flag name comes from the path, not the body. */
public record UpdateFlagRequest(
        @NotNull Variation defaultState,
        @Min(0) @Max(100) Integer rolloutPercentage,
        @Valid List<RuleDto> rules
) {
}
