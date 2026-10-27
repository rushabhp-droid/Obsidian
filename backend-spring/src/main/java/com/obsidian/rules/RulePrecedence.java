package com.obsidian.rules;

import com.obsidian.model.ListType;
import com.obsidian.model.RuleSource;
import com.obsidian.model.ThreatRule;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * The one place rule precedence lives (TODO.md §4.B). When several rules match
 * one indicator, the first match wins:
 * <ol>
 *   <li>org ADMIN_POLICY rule (either list): admins always win</li>
 *   <li>the user's unexpired WHITELIST, only if the org allows user bypass;
 *       an enterprise user's bypass also needs an expiry</li>
 *   <li>the user's BLACKLIST</li>
 *   <li>other org rules (crowd-immunity reports)</li>
 *   <li>global rules (no org, no user)</li>
 * </ol>
 * No match means VirusTotal / heuristics decide. Expired rules never match.
 */
public final class RulePrecedence {

    /**
     * Who the decision is for. A user scan has a userId; a daemon SYNC has only
     * the organization, so user rules never reach the firewall.
     */
    public record Scope(UUID orgId, UUID userId, boolean allowUserBypass) {

        public static Scope organization(UUID orgId, boolean allowUserBypass) {
            return new Scope(orgId, null, allowUserBypass);
        }
    }

    private RulePrecedence() {}

    public static Optional<ThreatRule> resolve(Collection<ThreatRule> candidates, Scope scope, Instant now) {
        List<ThreatRule> live = candidates.stream().filter(rule -> !rule.isExpired(now)).toList();

        Predicate<ThreatRule> orgRule = rule -> scope.orgId() != null
                && scope.orgId().equals(rule.getOrgId()) && rule.getUserId() == null;
        Predicate<ThreatRule> userRule = rule -> scope.userId() != null && scope.userId().equals(rule.getUserId());
        boolean enterpriseUser = scope.orgId() != null;

        return first(live, orgRule.and(rule -> rule.getSource() == RuleSource.ADMIN_POLICY))
                .or(() -> scope.allowUserBypass()
                        ? first(live, userRule.and(rule -> rule.getListType() == ListType.WHITELIST)
                                .and(rule -> !enterpriseUser || rule.getExpiresAt() != null))
                        : Optional.empty())
                .or(() -> first(live, userRule.and(rule -> rule.getListType() == ListType.BLACKLIST)))
                .or(() -> first(live, orgRule))
                .or(() -> first(live, ThreatRule::isGlobal));
    }

    private static Optional<ThreatRule> first(List<ThreatRule> rules, Predicate<ThreatRule> matches) {
        return rules.stream().filter(matches).findFirst();
    }
}
