package com.digitalocean.featureflags.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RolloutBucketerTest {

    private final RolloutBucketer bucketer = new RolloutBucketer();

    @Test
    void bucketIsDeterministicAcrossCalls() {
        int first = bucketer.bucketFor("new-checkout", "u-123");
        int second = bucketer.bucketFor("new-checkout", "u-123");
        assertThat(first).isEqualTo(second);
    }

    @Test
    void bucketIsAlwaysInRange() {
        for (int i = 0; i < 1000; i++) {
            int bucket = bucketer.bucketFor("flag", "user-" + i);
            assertThat(bucket).isBetween(0, 99);
        }
    }

    @Test
    void pctZeroIsAlwaysOutAndPctHundredIsAlwaysIn() {
        for (int i = 0; i < 200; i++) {
            assertThat(bucketer.inRollout("flag", "user-" + i, 0)).isFalse();
            assertThat(bucketer.inRollout("flag", "user-" + i, 100)).isTrue();
        }
    }

    @Test
    void exposureIsStickyAsPercentageRamps() {
        // A user inside the rollout at a lower percentage must remain inside at a higher one.
        for (int i = 0; i < 1000; i++) {
            String user = "user-" + i;
            if (bucketer.inRollout("flag", user, 20)) {
                assertThat(bucketer.inRollout("flag", user, 50)).isTrue();
            }
        }
    }

    @Test
    void bucketIsDecorrelatedAcrossFlags() {
        long differing = 0;
        for (int i = 0; i < 1000; i++) {
            String user = "user-" + i;
            if (bucketer.bucketFor("flag-a", user) != bucketer.bucketFor("flag-b", user)) {
                differing++;
            }
        }
        // Including the flag name in the hash means most users land in different buckets per flag.
        assertThat(differing).isGreaterThan(900);
    }

    @Test
    void distributionApproximatesConfiguredPercentage() {
        int sampleSize = 20_000;
        int pct = 30;
        long inRollout = 0;
        for (int i = 0; i < sampleSize; i++) {
            if (bucketer.inRollout("flag", "user-" + i, pct)) {
                inRollout++;
            }
        }
        double observed = (double) inRollout / sampleSize * 100;
        assertThat(observed).isCloseTo(pct, org.assertj.core.data.Offset.offset(2.0));
    }
}
