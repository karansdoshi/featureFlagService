package com.digitalocean.featureflags.storage;

import java.io.Serializable;
import java.util.Objects;

/** Composite primary key for {@link FlagOverrideEntity}: (flagName, userId). */
public class FlagOverrideId implements Serializable {

    private String flagName;
    private String userId;

    public FlagOverrideId() {
    }

    public FlagOverrideId(String flagName, String userId) {
        this.flagName = flagName;
        this.userId = userId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FlagOverrideId that)) {
            return false;
        }
        return Objects.equals(flagName, that.flagName) && Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(flagName, userId);
    }
}
