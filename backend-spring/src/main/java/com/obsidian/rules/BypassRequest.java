package com.obsidian.rules;

import com.obsidian.model.IndicatorType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** RuleService enforces when ttlMinutes is required and validates its range. */
public record BypassRequest(
        @NotNull IndicatorType indicatorType,
        @NotBlank @Size(max = 2048) String indicatorValue,
        Integer ttlMinutes) {}
