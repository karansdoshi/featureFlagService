package com.digitalocean.featureflags.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Storage knobs. {@code simulateOutage} makes {@link FlagStore} throw a
 * {@code DataAccessException}, exercising the DB-down fallback (evaluation) and 503 (writes)
 * paths without tearing down a real database. See DECISIONS.md / D-004.
 */
@Component
@ConfigurationProperties(prefix = "featureflags.storage")
public class StorageProperties {

    private boolean simulateOutage = false;

    public boolean isSimulateOutage() {
        return simulateOutage;
    }

    public void setSimulateOutage(boolean simulateOutage) {
        this.simulateOutage = simulateOutage;
    }
}
