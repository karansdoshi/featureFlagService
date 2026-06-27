package com.digitalocean.featureflags.api.dto;

import java.time.Instant;
import java.util.List;

/** Structured error body returned for all non-2xx responses handled by the advice. */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<FieldError> fieldErrors
) {
    public record FieldError(String field, String message) {
    }
}
