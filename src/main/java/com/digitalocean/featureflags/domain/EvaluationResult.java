package com.digitalocean.featureflags.domain;

/** The outcome of evaluating a flag for a context. */
public record EvaluationResult(String flag, boolean enabled, Reason reason) {

    public static EvaluationResult of(String flag, Variation variation, Reason reason) {
        return new EvaluationResult(flag, variation.asBoolean(), reason);
    }

    /** Safe-default outcome served when the definition cannot be resolved. */
    public static EvaluationResult fallback(String flag) {
        return new EvaluationResult(flag, false, Reason.FALLBACK);
    }
}
