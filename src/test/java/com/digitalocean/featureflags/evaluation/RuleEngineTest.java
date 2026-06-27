package com.digitalocean.featureflags.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.digitalocean.featureflags.domain.Attribute;
import com.digitalocean.featureflags.domain.EvaluationContext;
import com.digitalocean.featureflags.domain.EvaluationResult;
import com.digitalocean.featureflags.domain.FlagDefinition;
import com.digitalocean.featureflags.domain.Operator;
import com.digitalocean.featureflags.domain.Reason;
import com.digitalocean.featureflags.domain.Rule;
import com.digitalocean.featureflags.domain.Variation;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RuleEngineTest {

    private final RuleEngine engine = new RuleEngine(new RolloutBucketer());

    private static EvaluationContext ctx(String userId, String tier, String region) {
        return new EvaluationContext(userId, tier, region);
    }

    private static FlagDefinition flag(Variation dflt, Integer rollout,
                                       List<Rule> rules, Map<String, Variation> overrides) {
        return new FlagDefinition("flag", dflt, rollout, rules, overrides);
    }

    @Test
    void overrideWinsOverEverything() {
        Rule alwaysOn = new Rule(1, Attribute.SUBSCRIPTION_TIER, Operator.EQUALS, "PREMIUM", null, Variation.ON);
        FlagDefinition def = flag(Variation.ON, 100, List.of(alwaysOn), Map.of("u-1", Variation.OFF));

        EvaluationResult result = engine.evaluate(def, ctx("u-1", "PREMIUM", "US"));

        assertThat(result.enabled()).isFalse();
        assertThat(result.reason()).isEqualTo(Reason.OVERRIDE);
    }

    @Test
    void firstMatchingRuleWinsInOrder() {
        Rule first = new Rule(1, Attribute.REGION, Operator.EQUALS, "US", null, Variation.OFF);
        Rule second = new Rule(2, Attribute.SUBSCRIPTION_TIER, Operator.EQUALS, "FREE", null, Variation.ON);
        FlagDefinition def = flag(Variation.ON, 0, List.of(first, second), Map.of());

        EvaluationResult result = engine.evaluate(def, ctx("u-1", "FREE", "US"));

        assertThat(result.enabled()).isFalse(); // first rule (OFF) wins, not the second
        assertThat(result.reason()).isEqualTo(Reason.RULE_MATCH);
    }

    @Test
    void inOperatorMatchesAnyOfValues() {
        Rule rule = new Rule(1, Attribute.REGION, Operator.IN, null, List.of("IN", "US"), Variation.ON);
        FlagDefinition def = flag(Variation.OFF, 0, List.of(rule), Map.of());

        assertThat(engine.evaluate(def, ctx("u-1", "FREE", "IN")).reason()).isEqualTo(Reason.RULE_MATCH);
        assertThat(engine.evaluate(def, ctx("u-1", "FREE", "DE")).reason()).isEqualTo(Reason.DEFAULT);
    }

    @Test
    void notEqualsOperatorMatchesWhenDifferent() {
        Rule rule = new Rule(1, Attribute.SUBSCRIPTION_TIER, Operator.NOT_EQUALS, "FREE", null, Variation.ON);
        FlagDefinition def = flag(Variation.OFF, 0, List.of(rule), Map.of());

        assertThat(engine.evaluate(def, ctx("u-1", "PREMIUM", "US")).enabled()).isTrue();
        assertThat(engine.evaluate(def, ctx("u-1", "FREE", "US")).reason()).isEqualTo(Reason.DEFAULT);
    }

    @Test
    void rolloutAppliesOnlyToUnmatchedTraffic() {
        FlagDefinition fullRollout = flag(Variation.OFF, 100, List.of(), Map.of());
        EvaluationResult result = engine.evaluate(fullRollout, ctx("u-1", "FREE", "US"));
        assertThat(result.enabled()).isTrue();
        assertThat(result.reason()).isEqualTo(Reason.ROLLOUT);
    }

    @Test
    void fallsThroughToDefaultWhenNothingMatches() {
        FlagDefinition def = flag(Variation.OFF, 0, List.of(), Map.of());
        EvaluationResult result = engine.evaluate(def, ctx("u-1", "FREE", "US"));
        assertThat(result.enabled()).isFalse();
        assertThat(result.reason()).isEqualTo(Reason.DEFAULT);
    }

    @Test
    void ruleIsSkippedWhenItDoesNotMatch() {
        Rule rule = new Rule(1, Attribute.REGION, Operator.EQUALS, "US", null, Variation.ON);
        FlagDefinition def = flag(Variation.ON, 0, List.of(rule), Map.of());
        // Region DE does not match -> falls through to default ON.
        EvaluationResult result = engine.evaluate(def, ctx("u-1", "FREE", "DE"));
        assertThat(result.reason()).isEqualTo(Reason.DEFAULT);
        assertThat(result.enabled()).isTrue();
    }
}
