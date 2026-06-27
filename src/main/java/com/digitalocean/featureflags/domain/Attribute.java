package com.digitalocean.featureflags.domain;

import java.util.Arrays;

/**
 * The closed set of user-context attributes a rule can target. The JSON key (camelCase)
 * matches the fields of {@link EvaluationContext}; unknown keys are rejected at the API layer.
 */
public enum Attribute {
    USER_ID("userId"),
    SUBSCRIPTION_TIER("subscriptionTier"),
    REGION("region");

    private final String key;

    Attribute(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static Attribute fromKey(String key) {
        return Arrays.stream(values())
                .filter(a -> a.key.equals(key))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown attribute: " + key));
    }
}
