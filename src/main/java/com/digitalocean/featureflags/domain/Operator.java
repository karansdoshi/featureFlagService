package com.digitalocean.featureflags.domain;

import java.util.List;
import java.util.Objects;

/** How a rule compares a context value. EQUALS/NOT_EQUALS use {@code value}; IN uses {@code values}. */
public enum Operator {
    EQUALS {
        @Override
        public boolean matches(String contextValue, String value, List<String> values) {
            return Objects.equals(value, contextValue);
        }
    },
    NOT_EQUALS {
        @Override
        public boolean matches(String contextValue, String value, List<String> values) {
            return !Objects.equals(value, contextValue);
        }
    },
    IN {
        @Override
        public boolean matches(String contextValue, String value, List<String> values) {
            return values != null && values.contains(contextValue);
        }
    };

    public abstract boolean matches(String contextValue, String value, List<String> values);
}
