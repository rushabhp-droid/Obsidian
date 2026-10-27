package com.obsidian.scanner;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.obsidian.model.IndicatorType;
import com.obsidian.model.ThreatCategory;
import com.obsidian.model.Verdict;
import java.util.List;
import java.util.UUID;

/** The scan verdict: the worst indicator decides (docs/protocol.md, "Scanning"). */
public record ScanResponse(
        Verdict verdict,
        ThreatCategory category,
        int vtDetections,
        UUID scanId,
        List<IndicatorResult> indicators) {

    /** Where an indicator's verdict came from. */
    public enum Source { RULE, VIRUSTOTAL, NONE }

    public record IndicatorResult(
            IndicatorType indicatorType,
            String value,
            Verdict verdict,
            ThreatCategory category,
            int vtDetections,
            Source source,
            UUID ruleId,
            @JsonIgnore boolean bypassed) {}
}
