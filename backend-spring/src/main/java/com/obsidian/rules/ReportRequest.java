package com.obsidian.rules;

import com.obsidian.model.IndicatorType;
import com.obsidian.model.ThreatCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReportRequest(
        @NotNull IndicatorType indicatorType,
        @NotBlank @Size(max = 2048) String indicatorValue,
        ThreatCategory threatCategory) {}
