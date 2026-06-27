package com.digitalocean.featureflags.error;

/** Thrown when a CRUD/override operation targets a flag that does not exist (-> HTTP 404). */
public class FlagNotFoundException extends RuntimeException {
    public FlagNotFoundException(String name) {
        super("Flag not found: " + name);
    }
}
