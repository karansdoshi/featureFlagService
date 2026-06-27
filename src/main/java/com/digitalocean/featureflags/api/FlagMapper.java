package com.digitalocean.featureflags.api;

import com.digitalocean.featureflags.api.dto.FlagResponse;
import com.digitalocean.featureflags.api.dto.RuleDto;
import com.digitalocean.featureflags.domain.Attribute;
import com.digitalocean.featureflags.domain.Operator;
import com.digitalocean.featureflags.domain.Rule;
import com.digitalocean.featureflags.storage.FlagRecord;
import java.util.List;

/**
 * Maps between API DTOs and domain objects. Parsing {@code attribute}/{@code operator} strings
 * here is where unknown values and operator/operand mismatches become
 * {@link IllegalArgumentException}s, which the advice renders as 400.
 */
final class FlagMapper {

    private FlagMapper() {
    }

    static List<Rule> toDomainRules(List<RuleDto> dtos) {
        if (dtos == null) {
            return List.of();
        }
        return dtos.stream().map(FlagMapper::toDomain).toList();
    }

    static Rule toDomain(RuleDto dto) {
        Attribute attribute = Attribute.fromKey(dto.attribute());
        Operator operator = parseOperator(dto.operator());
        validateOperands(operator, dto);
        int order = dto.order() == null ? 0 : dto.order();
        return new Rule(order, attribute, operator, dto.value(), dto.values(), dto.result());
    }

    private static Operator parseOperator(String raw) {
        try {
            return Operator.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unknown operator: " + raw);
        }
    }

    private static void validateOperands(Operator operator, RuleDto dto) {
        if (operator == Operator.IN) {
            if (dto.values() == null || dto.values().isEmpty()) {
                throw new IllegalArgumentException("Operator IN requires a non-empty 'values' list");
            }
        } else if (dto.value() == null || dto.value().isBlank()) {
            throw new IllegalArgumentException("Operator " + operator + " requires a 'value'");
        }
    }

    static FlagResponse toResponse(FlagRecord record) {
        List<RuleDto> rules = record.rules().stream().map(FlagMapper::toDto).toList();
        return new FlagResponse(
                record.name(),
                record.defaultState(),
                record.rolloutPercentage(),
                rules,
                record.createdAt(),
                record.updatedAt());
    }

    private static RuleDto toDto(Rule rule) {
        List<String> values = (rule.values() == null || rule.values().isEmpty()) ? null : rule.values();
        return new RuleDto(
                rule.attribute().key(),
                rule.operator().name(),
                rule.value(),
                values,
                rule.result(),
                rule.order());
    }
}
