package com.digitalocean.featureflags.error;

/** Thrown when creating a flag whose name already exists (-> HTTP 409). */
public class DuplicateFlagException extends RuntimeException {
    public DuplicateFlagException(String name) {
        super("Flag already exists: " + name);
    }
}
