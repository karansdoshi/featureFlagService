package com.digitalocean.featureflags.service;

import com.digitalocean.featureflags.cache.FlagDefinitionCache;
import com.digitalocean.featureflags.domain.Variation;
import com.digitalocean.featureflags.error.FlagNotFoundException;
import com.digitalocean.featureflags.storage.FlagStore;
import org.springframework.stereotype.Service;

/**
 * Per-user override management. An override is its own row keyed by (flagName, userId); any
 * change evicts the cached definition so overrides take effect on the next evaluation.
 */
@Service
public class OverrideService {

    private final FlagStore store;
    private final FlagDefinitionCache cache;

    public OverrideService(FlagStore store, FlagDefinitionCache cache) {
        this.store = store;
        this.cache = cache;
    }

    public void set(String flagName, String userId, Variation state) {
        requireFlag(flagName);
        store.putOverride(flagName, userId, state);
        cache.evict(flagName);
    }

    public void remove(String flagName, String userId) {
        requireFlag(flagName);
        store.deleteOverride(flagName, userId); // idempotent: no-op if absent
        cache.evict(flagName);
    }

    private void requireFlag(String flagName) {
        if (!store.exists(flagName)) {
            throw new FlagNotFoundException(flagName);
        }
    }
}
