package com.digitalocean.featureflags.cache;

import com.digitalocean.featureflags.domain.FlagDefinition;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

/**
 * In-memory cache of assembled {@link FlagDefinition}s keyed by flag name (DESIGN.md
 * "Caching Design"). Backed by a {@link ConcurrentHashMap} for atomic get/put/remove with no
 * locking. No TTL: coherence is maintained by explicit eviction on every write.
 */
@Component
public class FlagDefinitionCache {

    private final ConcurrentMap<String, FlagDefinition> cache = new ConcurrentHashMap<>();

    public Optional<FlagDefinition> get(String flagName) {
        return Optional.ofNullable(cache.get(flagName));
    }

    public void put(String flagName, FlagDefinition definition) {
        cache.put(flagName, definition);
    }

    public void evict(String flagName) {
        cache.remove(flagName);
    }

    public void clear() {
        cache.clear();
    }

    public int size() {
        return cache.size();
    }
}
