package com.digitalocean.featureflags.domain;

import java.util.List;

/**
 * A single targeting rule: if the context's {@code attribute} satisfies {@code operator}
 * against {@code value}/{@code values}, the flag resolves to {@code result}.
 * Rules are evaluated in ascending {@code order}; first match wins.
 */
public record Rule(
        int order,
        Attribute attribute,
        Operator operator,
        String value,
        List<String> values,
        Variation result
) {
    public boolean matches(EvaluationContext context) {
        String contextValue = context.valueFor(attribute);
        if (contextValue == null) {
            return false;
        }
        return operator.matches(contextValue, value, values);
    }
}
