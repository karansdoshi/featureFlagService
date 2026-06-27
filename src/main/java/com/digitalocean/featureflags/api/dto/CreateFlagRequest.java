package com.digitalocean.featureflags.api.dto;

import com.digitalocean.featureflags.domain.Variation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record CreateFlagRequest(
        @NotBlank String name,
        @NotNull Variation defaultState,
        @Min(0) @Max(100) Integer rolloutPercentage,
        @Valid List<RuleDto> rules
) {
}
