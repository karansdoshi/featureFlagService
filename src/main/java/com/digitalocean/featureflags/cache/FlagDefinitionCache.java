package com.digitalocean.featureflags.cache;

import com.digitalocean.featureflags.domain.FlagDefinition;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * In-memory cache of assembled {@link FlagDefinition}s keyed by flag name (DESIGN.md
 * "Caching Design"). Backed by Caffeine.
 *
 * <p>Coherence is still driven by <em>explicit eviction on every write</em> (write-invalidate);
 * the {@code expireAfterWrite} TTL is only a safety net that bounds staleness if an eviction is
 * ever missed (e.g. a future multi-instance deployment where a write on another node would not
 * reach this node's cache). {@code maximumSize} bounds memory and {@code recordStats} exposes
 * hit/miss/eviction metrics (wired to Micrometer via {@code CaffeineCacheMetrics} when present).
 */
@Component
public class FlagDefinitionCache {

    private static final Logger log = LoggerFactory.getLogger(FlagDefinitionCache.class);

    private static final long MAX_ENTRIES = 10_000;
    private static final Duration TTL = Duration.ofMinutes(5);

    private final Cache<String, FlagDefinition> cache = Caffeine.newBuilder()
            .maximumSize(MAX_ENTRIES)
            .expireAfterWrite(TTL)
            .recordStats()
            .build();

    public Optional<FlagDefinition> get(String flagName) {
        FlagDefinition definition = cache.getIfPresent(flagName);
        if (log.isDebugEnabled()) {
            log.debug("Cache {} for flag '{}'", definition != null ? "hit" : "miss", flagName);
        }
        return Optional.ofNullable(definition);
    }

    public void put(String flagName, FlagDefinition definition) {
        cache.put(flagName, definition);
        log.debug("Cached definition for flag '{}'", flagName);
    }

    public void evict(String flagName) {
        cache.invalidate(flagName);
        log.debug("Evicted flag '{}' from cache", flagName);
    }

    public void clear() {
        cache.invalidateAll();
        log.debug("Cleared flag definition cache");
    }

    public long size() {
        return cache.estimatedSize();
    }
}
