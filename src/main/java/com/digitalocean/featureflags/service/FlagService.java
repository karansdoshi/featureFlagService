package com.digitalocean.featureflags.service;

import com.digitalocean.featureflags.cache.FlagDefinitionCache;
import com.digitalocean.featureflags.error.DuplicateFlagException;
import com.digitalocean.featureflags.error.FlagNotFoundException;
import com.digitalocean.featureflags.storage.FlagRecord;
import com.digitalocean.featureflags.storage.FlagStore;
import com.digitalocean.featureflags.storage.FlagWrite;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Flag CRUD. Persists through {@link FlagStore} (source of truth first) and then evicts the
 * cache entry so the next evaluation lazily reloads (write-invalidate; DESIGN.md / D-010).
 */
@Service
public class FlagService {

    private final FlagStore store;
    private final FlagDefinitionCache cache;

    public FlagService(FlagStore store, FlagDefinitionCache cache) {
        this.store = store;
        this.cache = cache;
    }

    public List<FlagRecord> list() {
        return store.findAll();
    }

    public FlagRecord get(String name) {
        return store.find(name).orElseThrow(() -> new FlagNotFoundException(name));
    }

    public FlagRecord create(FlagWrite write) {
        if (store.exists(write.name())) {
            throw new DuplicateFlagException(write.name());
        }
        FlagRecord created = store.create(write);
        cache.evict(write.name());
        return created;
    }

    public FlagRecord update(String name, FlagWrite write) {
        FlagRecord updated = store.update(name, write); // throws FlagNotFoundException if absent
        cache.evict(name);
        return updated;
    }

    public void delete(String name) {
        store.delete(name); // throws FlagNotFoundException if absent
        cache.evict(name);
    }
}
