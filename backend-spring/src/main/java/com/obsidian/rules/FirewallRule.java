package com.obsidian.rules;

import com.obsidian.model.IndicatorType;
import java.util.UUID;

/** One indicator a daemon must block: an entry of the SYNC message. */
public record FirewallRule(UUID ruleId, IndicatorType indicatorType, String value) {}
