package com.obsidian.rules;

import com.obsidian.model.ThreatRule;
import java.time.Instant;

/** Published by RuleService inside its transaction; relayed to Redis after commit. */
public record RuleChanged(ThreatRule rule, Instant at) {}
