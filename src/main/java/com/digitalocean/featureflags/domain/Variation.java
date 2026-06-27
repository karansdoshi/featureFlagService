package com.digitalocean.featureflags.domain;

/** The result a rule (or the flag default) resolves to. */
public enum Variation {
    ON,
    OFF;

    public boolean asBoolean() {
        return this == ON;
    }

    public static Variation of(boolean enabled) {
        return enabled ? ON : OFF;
    }
}
