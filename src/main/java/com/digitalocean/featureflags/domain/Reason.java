package com.digitalocean.featureflags.domain;

/** Why an evaluation produced its result. Always present in the response. */
public enum Reason {
    OVERRIDE,
    RULE_MATCH,
    ROLLOUT,
    DEFAULT,
    FALLBACK
}
