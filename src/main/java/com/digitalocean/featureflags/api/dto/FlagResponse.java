package com.digitalocean.featureflags.api.dto;

import com.digitalocean.featureflags.domain.Variation;
import java.time.Instant;
import java.util.List;

public record FlagResponse(
        String name,
        Variation defaultState,
        Integer rolloutPercentage,
        List<RuleDto> rules,
        Instant createdAt,
        Instant updatedAt
) {
}
