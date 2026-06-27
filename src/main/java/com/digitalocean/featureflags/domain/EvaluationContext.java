package com.digitalocean.featureflags.domain;

/** The validated user context an evaluation runs against. */
public record EvaluationContext(String userId, String subscriptionTier, String region) {

    public String valueFor(Attribute attribute) {
        return switch (attribute) {
            case USER_ID -> userId;
            case SUBSCRIPTION_TIER -> subscriptionTier;
            case REGION -> region;
        };
    }
}
