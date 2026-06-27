package com.digitalocean.featureflags.evaluation;

import com.google.common.hash.Hashing;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

/**
 * Deterministic percentage-rollout bucketing (DESIGN.md / D-013).
 *
 * <p>A user is assigned a stable bucket in {@code [0, 99]} from
 * {@code Murmur3_128(flagName + ":" + userId)}. Including the flag name decorrelates buckets
 * across flags; Murmur3 gives a near-uniform distribution so observed exposure matches the
 * configured percentage. The function is pure, so exposure is sticky with no stored state.
 */
@Component
public class RolloutBucketer {

    public boolean inRollout(String flagName, String userId, int rolloutPercentage) {
        if (rolloutPercentage <= 0) {
            return false;
        }
        if (rolloutPercentage >= 100) {
            return true;
        }
        return bucketFor(flagName, userId) < rolloutPercentage;
    }

    /** The stable 0..99 bucket for a (flag, user) pair. Exposed for testing/inspection. */
    public int bucketFor(String flagName, String userId) {
        String key = flagName + ":" + userId;
        long hash = Hashing.murmur3_128()
                .hashString(key, StandardCharsets.UTF_8)
                .asLong();
        return (int) Math.floorMod(hash, 100);
    }
}
