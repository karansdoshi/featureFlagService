package com.digitalocean.featureflags.api.dto;

import com.digitalocean.featureflags.domain.Variation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Wire form of a targeting rule. {@code attribute}/{@code operator} are strings validated and
 * parsed into the domain enums (unknown values -> 400). {@code value} is used by EQUALS/
 * NOT_EQUALS; {@code values} by IN.
 */
public record RuleDto(
        @NotBlank String attribute,
        @NotBlank String operator,
        String value,
        List<String> values,
        @NotNull Variation result,
        @NotNull Integer order
) {
}
