// Shapes follow docs/protocol.md, which wins over this file; change both together.
// backend-spring's ApiContractTest reads this file and fails if a response drifts.

export type IndicatorType = "EMAIL" | "PHONE" | "URL" | "DOMAIN" | "IP" | "FILE_HASH";
export type ListType = "BLACKLIST" | "WHITELIST";
export type ThreatCategory = "SAFE" | "SPAM" | "PHISHING" | "MALWARE" | "MALICIOUS";
export type RuleSource = "VIRUSTOTAL" | "USER_REPORT" | "USER_BYPASS" | "ADMIN_POLICY";
export type Role = "CONSUMER" | "EMPLOYEE" | "ADMIN";

/** POST /api/v1/scan/unified verdicts. PENDING is never shown as safe. */
export type Verdict = "MALICIOUS" | "SUSPICIOUS" | "CLEAN" | "PENDING";
/** scan_logs.verdict adds BYPASSED. */
export type ScanVerdict = Verdict | "BYPASSED";
/** scan_logs.channel; CODE belongs to Code Guard [P1] and isn't shown yet. */
export type Channel = "EMAIL" | "SMS" | "WHATSAPP" | "FIREWALL";

export interface ThreatRule {
  id: string;
  orgId: string | null;
  userId: string | null;
  indicatorType: IndicatorType;
  /** Normalized lowercase. */
  indicatorValue: string;
  listType: ListType;
  threatCategory: ThreatCategory;
  source: RuleSource;
  confidenceScore: number;
  /** TTL bypass lives only here. */
  expiresAt: string | null;
  updatedAt: string;
}

export interface ScanLog {
  id: string;
  channel: Channel;
  sender: string | null;
  indicatorChecked: string | null;
  verdict: ScanVerdict;
  vtPositives: number;
  createdAt: string;
}

export interface EndpointAgent {
  id: string;
  hostname: string;
  lastHeartbeatAt: string | null;
  createdAt: string;
}

/** POST /api/v1/agents returns the bearer token exactly once. */
export interface RegisteredAgent extends EndpointAgent {
  token: string;
}

export type AuthCheck = "PASS" | "FAIL" | "NONE";

export interface EmailLink {
  url: string;
  verdict: Verdict;
  vtDetections: number;
}

export interface EmailAttachment {
  filename: string;
  sha256: string;
  verdict: Verdict;
  vtDetections: number;
}

export interface ScannedEmail {
  id: string;
  from: string;
  subject: string;
  receivedAt: string;
  /** Parsed only from the topmost mx.google.com Authentication-Results header. */
  spf: AuthCheck;
  dkim: AuthCheck;
  dmarc: AuthCheck;
  /** All three pass and the sender domain is on the allowlist. */
  verifiedOfficial: boolean;
  verdict: Verdict;
  links: EmailLink[];
  attachments: EmailAttachment[];
}

export interface SessionInfo {
  email: string;
  displayName: string;
  role: Role;
  organization: {
    id: string;
    name: string;
    policyAllowUserBypass: boolean;
  } | null;
}

export interface GmailConnection {
  connected: boolean;
  address: string | null;
  lastPolledAt: string | null;
}

/** Code Guard [P1]: GitHub pushes, pull requests and releases. */
export type CodeTrigger = "PUSH" | "PR" | "RELEASE";
export type CodeFindingType = "SECRET" | "VULN_DEPENDENCY" | "MALICIOUS_BINARY" | "VULN_BINARY";
export type CodeSeverity = "CRITICAL" | "HIGH" | "MEDIUM" | "LOW";
export type CodeFindingStatus = "OPEN" | "REVOKED" | "FALSE_POSITIVE";

export interface CodeFinding {
  id: string;
  repoFullName: string;
  trigger: CodeTrigger;
  /** Commit SHA, or the release tag. */
  gitRef: string;
  findingType: CodeFindingType;
  severity: CodeSeverity;
  /** gitleaks rule, OSV / CVE id, or "VT:<detections>". */
  ruleId: string;
  /** path:line, package@version, or the asset filename. */
  location: string | null;
  /** SHA-256 of a leaked secret. The secret itself is never stored or sent. */
  secretFingerprint: string | null;
  status: CodeFindingStatus;
  createdAt: string;
}

/** Reports from the B2C Android app (/api/b2c/v1) that need an admin; GET /api/v1/consumer-reports. */
export type ConsumerReportStatus = "WAITING" | "PROTECTED" | "BLOCKED" | "DISMISSED";

export interface ConsumerReportQueueItem {
  indicatorType: IndicatorType;
  indicatorValue: string;
  threatCategory: ThreatCategory;
  status: ConsumerReportStatus;
  /** A protected domain can't be approved for a global block. */
  protectedDomain: boolean;
  /** Verified consumers who reported it in the last 7 days. */
  reporters: number;
  reportersNeeded: number;
  notes: string[];
  firstReportedAt: string;
  lastReportedAt: string;
}

/** Settings → Organization (GET/PUT /api/v1/org). Admins only. */
export interface OrgSettings {
  id: string;
  name: string;
  policyAllowUserBypass: boolean;
  memberCount: number;
  adminCount: number;
  createdAt: string;
}

/** Settings → Users & roles (GET /api/v1/org/members). */
export interface Member {
  id: string;
  email: string;
  displayName: string;
  role: Exclude<Role, "CONSUMER">;
  passkeys: number;
  gmailConnected: boolean;
  /** The signed-in admin's own row. */
  you: boolean;
  createdAt: string;
}

export interface AddMemberRequest {
  email: string;
  displayName: string;
  role: Exclude<Role, "CONSUMER">;
  /** At least 12 characters; the admin hands it to the member. */
  temporaryPassword: string;
}

export type AuditAction =
  | "REPORT"
  | "BYPASS"
  | "BYPASS_EXPIRED"
  | "RULE_EDIT"
  | "AGENT_REGISTER"
  | "CODE_FINDING_STATUS"
  | "CONSUMER_REPORT"
  | "MEMBER_ADD"
  | "MEMBER_ROLE"
  | "MEMBER_REMOVE"
  | "ORG_UPDATE";

export interface AuditActor {
  id: string;
  email: string;
  displayName: string;
}

/** GET /api/v1/audit: newest first, read-only. */
export interface AuditEntry {
  id: string;
  action: AuditAction;
  /** null for automatic changes (VirusTotal, reporter thresholds) and removed accounts. */
  actor: AuditActor | null;
  orgId: string | null;
  ruleId: string | null;
  details: Record<string, unknown>;
  createdAt: string;
}

/** GET /api/v1/audit/{id}: the record, the rule as it is now, and the rule's history. */
export interface AuditDetail {
  entry: AuditEntry;
  rule: ThreatRule | null;
  ruleHistory: AuditEntry[];
}

export interface ReportCounts {
  total: number;
  malicious: number;
  suspicious: number;
  pending: number;
  clean: number;
  bypassed: number;
}

export interface ReportDay {
  /** UTC date, YYYY-MM-DD. */
  date: string;
  counts: ReportCounts;
}

export interface ReportSource {
  channel: Channel | "CODE";
  counts: ReportCounts;
}

/** Rules in force now that the caller can see. */
export interface ReportRuleCounts {
  blacklist: number;
  whitelist: number;
  organization: number;
  global: number;
  personal: number;
}

/** GET /api/v1/reports/summary?days=N: scan verdicts per UTC day and source. */
export interface ReportSummary {
  days: number;
  from: string;
  to: string;
  /** ORGANIZATION for admins, USER for everyone else. */
  scope: "ORGANIZATION" | "USER";
  totals: ReportCounts;
  byDay: ReportDay[];
  bySource: ReportSource[];
  rules: ReportRuleCounts;
}

/** One entry of a daemon's SYNC. */
export interface EnforcedRule {
  ruleId: string;
  indicatorType: IndicatorType;
  value: string;
}

/** GET /api/v1/agents/{id}: the daemon and the block set it enforces. */
export interface AgentDetail {
  agent: EndpointAgent;
  enforced: EnforcedRule[];
}

/** Server → browser messages on /ws/dashboard. */
export type DashboardEvent =
  | {
      type: "BLOCK" | "BYPASS";
      ruleId: string;
      indicator_type: IndicatorType;
      value: string;
      at: string;
    }
  | { type: "SCAN"; scan: ScanLog }
  | { type: "HEARTBEAT"; agentId: string; hostname: string; at: string }
  /** Admins only. */
  | { type: "CODE_FINDING"; finding: CodeFinding };

export interface ReportRequest {
  indicatorType: IndicatorType;
  indicatorValue: string;
  threatCategory?: ThreatCategory;
}

export interface BypassRequest {
  indicatorType: IndicatorType;
  indicatorValue: string;
  /** Required for enterprise users; the TTL picker is tracked as P1 in TODO.md. */
  ttlMinutes?: number;
}
