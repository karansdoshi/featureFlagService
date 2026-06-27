package com.digitalocean.featureflags.domain;

import java.util.List;
import java.util.Map;

/**
 * The complete, self-contained definition the rule engine needs to evaluate a flag:
 * its default, rollout percentage, ordered rules, and per-user overrides. This is the
 * unit that is cached by flag name.
 *
 * @param overrides userId -> forced {@link Variation}
 */
public record FlagDefinition(
        String name,
        Variation defaultState,
        Integer rolloutPercentage,
        List<Rule> rules,
        Map<String, Variation> overrides
) {
    public int rolloutPercentageOrZero() {
        return rolloutPercentage == null ? 0 : rolloutPercentage;
    }
}
