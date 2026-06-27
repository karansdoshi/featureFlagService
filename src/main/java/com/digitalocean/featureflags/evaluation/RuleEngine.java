package com.digitalocean.featureflags.evaluation;

import com.digitalocean.featureflags.domain.EvaluationContext;
import com.digitalocean.featureflags.domain.EvaluationResult;
import com.digitalocean.featureflags.domain.FlagDefinition;
import com.digitalocean.featureflags.domain.Reason;
import com.digitalocean.featureflags.domain.Rule;
import com.digitalocean.featureflags.domain.Variation;
import org.springframework.stereotype.Component;

/**
 * Pure evaluation of a {@link FlagDefinition} against an {@link EvaluationContext}.
 *
 * <p>Precedence (most explicit signal wins): override -> rules (first match by order) ->
 * percentage rollout -> default. See DESIGN.md "Evaluation Precedence".
 */
@Component
public class RuleEngine {

    private final RolloutBucketer bucketer;

    public RuleEngine(RolloutBucketer bucketer) {
        this.bucketer = bucketer;
    }

    public EvaluationResult evaluate(FlagDefinition definition, EvaluationContext context) {
        // 1. Per-user override always wins.
        Variation override = definition.overrides().get(context.userId());
        if (override != null) {
            return EvaluationResult.of(definition.name(), override, Reason.OVERRIDE);
        }

        // 2. First matching targeting rule, in ascending order.
        for (Rule rule : definition.rules()) {
            if (rule.matches(context)) {
                return EvaluationResult.of(definition.name(), rule.result(), Reason.RULE_MATCH);
            }
        }

        // 3. Percentage rollout governs unmatched traffic only.
        if (bucketer.inRollout(definition.name(), context.userId(), definition.rolloutPercentageOrZero())) {
            return new EvaluationResult(definition.name(), true, Reason.ROLLOUT);
        }

        // 4. Default is the safety net.
        return EvaluationResult.of(definition.name(), definition.defaultState(), Reason.DEFAULT);
    }
}
