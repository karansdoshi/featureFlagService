package com.digitalocean.featureflags.service;

import com.digitalocean.featureflags.cache.FlagDefinitionCache;
import com.digitalocean.featureflags.domain.EvaluationContext;
import com.digitalocean.featureflags.domain.EvaluationResult;
import com.digitalocean.featureflags.domain.FlagDefinition;
import com.digitalocean.featureflags.error.FlagNotFoundException;
import com.digitalocean.featureflags.evaluation.RuleEngine;
import com.digitalocean.featureflags.storage.FlagStore;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

/**
 * Evaluation read path: cache-aside load of the definition, then the pure {@link RuleEngine}.
 *
 * <p>Fallback contract (DECISIONS.md / D-012): a cache hit is served even if the DB is down; on
 * a cache miss we load from the DB. A definitively-unknown flag (DB reachable) yields a 404; a
 * DB outage with nothing cached yields a safe-default {@code FALLBACK} (enabled=false), so a
 * transient infra failure never fails the caller.
 */
@Service
public class EvaluationService {

    private static final Logger log = LoggerFactory.getLogger(EvaluationService.class);

    private final FlagStore store;
    private final FlagDefinitionCache cache;
    private final RuleEngine ruleEngine;

    public EvaluationService(FlagStore store, FlagDefinitionCache cache, RuleEngine ruleEngine) {
        this.store = store;
        this.cache = cache;
        this.ruleEngine = ruleEngine;
    }

    public EvaluationResult evaluate(String flagName, EvaluationContext context) {
        // Cache hit: serve regardless of DB state (degraded-but-available on outage).
        Optional<FlagDefinition> cached = cache.get(flagName);
        if (cached.isPresent()) {
            return ruleEngine.evaluate(cached.get(), context);
        }

        // Cache miss: load from the source of truth.
        try {
            FlagDefinition definition = store.loadDefinition(flagName)
                    // A definitively-unknown flag (DB reachable) is a client error -> 404,
                    // surfacing typos/misconfiguration rather than silently returning OFF.
                    .orElseThrow(() -> new FlagNotFoundException(flagName));
            cache.put(flagName, definition);
            return ruleEngine.evaluate(definition, context);
        } catch (DataAccessException ex) {
            // DB down and nothing cached: serve safe-default rather than fail the caller.
            log.warn("Database unavailable and no cached definition for flag '{}'; serving FALLBACK",
                    flagName, ex);
            return EvaluationResult.fallback(flagName);
        }
    }
}
