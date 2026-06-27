package com.digitalocean.featureflags.storage;

import com.digitalocean.featureflags.domain.Rule;
import com.digitalocean.featureflags.domain.Variation;
import java.util.List;

/** A create/update request for a flag's persistent state (overrides are managed separately). */
public record FlagWrite(
        String name,
        Variation defaultState,
        Integer rolloutPercentage,
        List<Rule> rules
) {
}
