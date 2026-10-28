import type { AuditAction, AuditEntry } from "@/lib/types";

// How audit records read in the dashboard. The server stores facts; these turn them into sentences.

export type AuditCategory = "rules" | "consumer" | "members" | "settings" | "code" | "agents";

export const actionLabel: Record<AuditAction, string> = {
  REPORT: "Indicator reported",
  BYPASS: "Bypass granted",
  BYPASS_EXPIRED: "Bypass expired",
  RULE_EDIT: "Rule edited",
  AGENT_REGISTER: "Agent registered",
  CODE_FINDING_STATUS: "Finding status changed",
  CONSUMER_REPORT: "Consumer report blocked globally",
  MEMBER_ADD: "Member added",
  MEMBER_ROLE: "Role changed",
  MEMBER_REMOVE: "Member removed",
  ORG_UPDATE: "Organization settings changed",
};

export const categoryOf: Record<AuditAction, AuditCategory> = {
  REPORT: "rules",
  BYPASS: "rules",
  BYPASS_EXPIRED: "rules",
  RULE_EDIT: "rules",
  AGENT_REGISTER: "agents",
  CODE_FINDING_STATUS: "code",
  CONSUMER_REPORT: "consumer",
  MEMBER_ADD: "members",
  MEMBER_ROLE: "members",
  MEMBER_REMOVE: "members",
  ORG_UPDATE: "settings",
};

export const categoryLabel: Record<AuditCategory, string> = {
  rules: "Rules",
  consumer: "Consumer reports",
  members: "Members",
  settings: "Settings",
  code: "Code",
  agents: "Endpoints",
};

const str = (value: unknown) => (value === null || value === undefined ? null : String(value));

/** What the record is about: an indicator, a member, the organization. */
export function resourceOf(entry: AuditEntry): string {
  const d = entry.details;
  return (
    str(d.indicatorValue) ??
    str(d.memberEmail) ??
    str(d.hostname) ??
    (entry.action === "ORG_UPDATE" ? "Organization" : null) ??
    (entry.ruleId ? entry.ruleId.slice(0, 8) : "—")
  );
}

/** "previous → new" where the record has one. */
export function changeOf(entry: AuditEntry): string {
  const d = entry.details;
  if (d.listType) return `${str(d.previousListType) ?? "None"} → ${str(d.listType)}`;
  if (d.previous !== undefined && d.new !== undefined) return `${str(d.previous)} → ${str(d.new)}`;
  if (entry.action === "MEMBER_ADD") return `None → ${str(d.role)}`;
  if (entry.action === "MEMBER_REMOVE") return `${str(d.role)} → Removed`;
  if (entry.action === "ORG_UPDATE") {
    return Object.entries(d)
      .map(([field, change]) => {
        const c = change as { previous?: unknown; new?: unknown };
        return `${field === "policyAllowUserBypass" ? "Employee bypass" : field}: ${str(c.previous)} → ${str(c.new)}`;
      })
      .join("; ");
  }
  return "—";
}

/** Who did it, or what did it when nobody did. */
export function actorOf(entry: AuditEntry): string {
  if (entry.actor) return entry.actor.displayName;
  if (entry.action === "CONSUMER_REPORT") {
    return entry.details.promotedBy === "VIRUSTOTAL" ? "VirusTotal" : entry.details.promotedBy === "REPORTERS" ? "Consumer reporters" : "System";
  }
  return "Removed account";
}

/** Who the change applies to: one person, the organization, or everyone (a promoted consumer report). */
export function scopeOf(entry: AuditEntry): string {
  const scope = entry.details.scope;
  if (scope === "USER") return "Personal";
  if (scope === "GLOBAL" || !entry.orgId) return "Global";
  return "Organization";
}

export function recordId(entry: AuditEntry) {
  return `AUD-${entry.id.slice(0, 6).toUpperCase()}`;
}
