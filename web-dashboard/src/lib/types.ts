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
  /** TTL bypass lives only here (CLAUDE.md). */
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
  | { type: "HEARTBEAT"; agentId: string; hostname: string; at: string };

export interface ReportRequest {
  indicatorType: IndicatorType;
  indicatorValue: string;
  threatCategory?: ThreatCategory;
}

export interface BypassRequest {
  indicatorType: IndicatorType;
  indicatorValue: string;
  /** Required for enterprise users (TODO.md §6); the TTL UI itself is [P1]. */
  ttlMinutes?: number;
}
