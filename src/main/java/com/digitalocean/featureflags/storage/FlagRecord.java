package com.digitalocean.featureflags.storage;

import com.digitalocean.featureflags.domain.Rule;
import com.digitalocean.featureflags.domain.Variation;
import java.time.Instant;
import java.util.List;

/** A flag's persisted state as returned to the API layer (CRUD responses). */
public record FlagRecord(
        String name,
        Variation defaultState,
        Integer rolloutPercentage,
        List<Rule> rules,
        Instant createdAt,
        Instant updatedAt
) {
}
