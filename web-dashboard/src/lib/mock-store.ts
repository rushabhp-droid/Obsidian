// In-browser sample data used while NEXT_PUBLIC_API_MODE isn't "live". It keeps
// the same shapes as the real API so pages don't branch on the mode, and Report /
// Bypass mutate it so the demo flow works before Spring is up. Names, domains
// (.example) and addresses (TEST-NET) are illustrative.

import type {
  BypassRequest,
  CodeFinding,
  ConsumerReportQueueItem,
  DashboardEvent,
  EndpointAgent,
  GmailConnection,
  IndicatorType,
  RegisteredAgent,
  ReportRequest,
  ScanLog,
  ScannedEmail,
  SessionInfo,
  ThreatRule,
} from "./types";
import { clearMockSignedOut, markMockSignedOut } from "./dashboard-tab-session";

const ORG_ID = "6f1c2a4e-0b7d-4c55-9a51-2d8e5b3c9f10";
const USER_ID = "b2e4d6f8-1a3c-4e5f-8a9b-0c1d2e3f4a5b";

const start = Date.now();
const ago = (minutes: number) => new Date(start - minutes * 60_000).toISOString();
const inMinutes = (minutes: number) => new Date(start + minutes * 60_000).toISOString();
const id = () => crypto.randomUUID();

const session: SessionInfo = {
  email: "avery.lin@aster.example",
  displayName: "Avery Lin",
  role: "ADMIN",
  organization: { id: ORG_ID, name: "Aster Research", policyAllowUserBypass: true },
};

let rules: ThreatRule[] = [
  {
    id: "4a0e5c7f-31d2-4b8e-9f60-1c2d3e4f5a01",
    orgId: ORG_ID,
    userId: null,
    indicatorType: "DOMAIN",
    indicatorValue: "testsafebrowsing.appspot.com",
    listType: "BLACKLIST",
    threatCategory: "PHISHING",
    source: "USER_REPORT",
    confidenceScore: 100,
    expiresAt: null,
    updatedAt: ago(18),
  },
  {
    id: "4a0e5c7f-31d2-4b8e-9f60-1c2d3e4f5a02",
    orgId: ORG_ID,
    userId: null,
    indicatorType: "URL",
    indicatorValue: "http://login-verify.aster-payroll.example/reset",
    listType: "BLACKLIST",
    threatCategory: "PHISHING",
    source: "VIRUSTOTAL",
    confidenceScore: 92,
    expiresAt: null,
    updatedAt: ago(64),
  },
  {
    id: "4a0e5c7f-31d2-4b8e-9f60-1c2d3e4f5a03",
    orgId: ORG_ID,
    userId: null,
    indicatorType: "IP",
    indicatorValue: "203.0.113.45",
    listType: "BLACKLIST",
    threatCategory: "MALWARE",
    source: "ADMIN_POLICY",
    confidenceScore: 100,
    expiresAt: null,
    updatedAt: ago(190),
  },
  {
    id: "4a0e5c7f-31d2-4b8e-9f60-1c2d3e4f5a04",
    orgId: ORG_ID,
    userId: null,
    indicatorType: "EMAIL",
    indicatorValue: "billing@invoice-verify.example",
    listType: "BLACKLIST",
    threatCategory: "SPAM",
    source: "USER_REPORT",
    confidenceScore: 100,
    expiresAt: null,
    updatedAt: ago(255),
  },
  {
    id: "4a0e5c7f-31d2-4b8e-9f60-1c2d3e4f5a05",
    orgId: ORG_ID,
    userId: null,
    indicatorType: "PHONE",
    indicatorValue: "+15550100",
    listType: "BLACKLIST",
    threatCategory: "SPAM",
    source: "USER_REPORT",
    confidenceScore: 100,
    expiresAt: null,
    updatedAt: ago(410),
  },
  {
    id: "4a0e5c7f-31d2-4b8e-9f60-1c2d3e4f5a06",
    orgId: ORG_ID,
    userId: null,
    indicatorType: "FILE_HASH",
    indicatorValue: "5f2b8a0c94d1e3b7a6c9f0e1d2c3b4a5968778695a4b3c2d1e0f9a8b7c6d5e4f",
    listType: "BLACKLIST",
    threatCategory: "MALWARE",
    source: "VIRUSTOTAL",
    confidenceScore: 97,
    expiresAt: null,
    updatedAt: ago(520),
  },
  {
    id: "4a0e5c7f-31d2-4b8e-9f60-1c2d3e4f5a07",
    orgId: ORG_ID,
    userId: null,
    indicatorType: "DOMAIN",
    indicatorValue: "docs.aster.example",
    listType: "WHITELIST",
    threatCategory: "SAFE",
    source: "ADMIN_POLICY",
    confidenceScore: 100,
    expiresAt: null,
    updatedAt: ago(1300),
  },
  {
    id: "4a0e5c7f-31d2-4b8e-9f60-1c2d3e4f5a08",
    orgId: ORG_ID,
    userId: USER_ID,
    indicatorType: "DOMAIN",
    indicatorValue: "delivery-status.example",
    listType: "WHITELIST",
    threatCategory: "SAFE",
    source: "USER_BYPASS",
    confidenceScore: 100,
    expiresAt: inMinutes(12),
    updatedAt: ago(3),
  },
];

let scans: ScanLog[] = [
  { id: "c1000006-0000-4000-8000-000000000006", channel: "WHATSAPP", sender: "Priya Nair (saved contact)", indicatorChecked: "http://testsafebrowsing.appspot.com/s/phishing.html", verdict: "MALICIOUS", vtPositives: 9, createdAt: ago(19) },
  { id: "c1000005-0000-4000-8000-000000000005", channel: "FIREWALL", sender: "build-linux-01", indicatorChecked: "203.0.113.45", verdict: "MALICIOUS", vtPositives: 6, createdAt: ago(47) },
  { id: "c1000004-0000-4000-8000-000000000004", channel: "EMAIL", sender: "payroll@aster-payroll.example", indicatorChecked: "http://login-verify.aster-payroll.example/reset", verdict: "MALICIOUS", vtPositives: 14, createdAt: ago(65) },
  { id: "c1000003-0000-4000-8000-000000000003", channel: "SMS", sender: "+15550100", indicatorChecked: "http://delivery-status.example/track/88231", verdict: "SUSPICIOUS", vtPositives: 2, createdAt: ago(122) },
  { id: "c1000002-0000-4000-8000-000000000002", channel: "EMAIL", sender: "noreply@delivery-status.example", indicatorChecked: "https://delivery-status.example/parcel", verdict: "PENDING", vtPositives: 0, createdAt: ago(131) },
  { id: "c1000001-0000-4000-8000-000000000001", channel: "EMAIL", sender: "billing@invoice-verify.example", indicatorChecked: "5f2b8a0c94d1e3b7a6c9f0e1d2c3b4a5968778695a4b3c2d1e0f9a8b7c6d5e4f", verdict: "MALICIOUS", vtPositives: 31, createdAt: ago(256) },
  { id: "c1000000-0000-4000-8000-000000000000", channel: "EMAIL", sender: "it-support@aster.example", indicatorChecked: "https://status.aster.example", verdict: "CLEAN", vtPositives: 0, createdAt: ago(380) },
];

let agents: EndpointAgent[] = [
  { id: "a9000001-0000-4000-8000-000000000001", hostname: "build-linux-01", lastHeartbeatAt: new Date(start - 4_000).toISOString(), createdAt: ago(2900) },
  { id: "a9000002-0000-4000-8000-000000000002", hostname: "avery-thinkpad", lastHeartbeatAt: ago(41), createdAt: ago(1500) },
  { id: "a9000003-0000-4000-8000-000000000003", hostname: "demo-laptop", lastHeartbeatAt: null, createdAt: ago(20) },
];

const emails: ScannedEmail[] = [
  {
    id: "e5000004-0000-4000-8000-000000000004",
    from: "Payroll Team <payroll@aster-payroll.example>",
    subject: "Action required: confirm your salary account",
    receivedAt: ago(65),
    spf: "FAIL",
    dkim: "NONE",
    dmarc: "FAIL",
    verifiedOfficial: false,
    verdict: "MALICIOUS",
    links: [{ url: "http://login-verify.aster-payroll.example/reset", verdict: "MALICIOUS", vtDetections: 14 }],
    attachments: [],
  },
  {
    id: "e5000003-0000-4000-8000-000000000003",
    from: "Courier Updates <noreply@delivery-status.example>",
    subject: "Your parcel is waiting for delivery",
    receivedAt: ago(131),
    spf: "PASS",
    dkim: "PASS",
    dmarc: "PASS",
    verifiedOfficial: false,
    verdict: "PENDING",
    links: [{ url: "https://delivery-status.example/parcel", verdict: "PENDING", vtDetections: 0 }],
    attachments: [],
  },
  {
    id: "e5000002-0000-4000-8000-000000000002",
    from: "Accounts <billing@invoice-verify.example>",
    subject: "Invoice #4471 is overdue",
    receivedAt: ago(256),
    spf: "PASS",
    dkim: "PASS",
    dmarc: "FAIL",
    verifiedOfficial: false,
    verdict: "MALICIOUS",
    links: [],
    attachments: [
      {
        filename: "invoice-4471.pdf.exe",
        sha256: "5f2b8a0c94d1e3b7a6c9f0e1d2c3b4a5968778695a4b3c2d1e0f9a8b7c6d5e4f",
        verdict: "MALICIOUS",
        vtDetections: 31,
      },
    ],
  },
  {
    id: "e5000001-0000-4000-8000-000000000001",
    from: "IT Service Desk <it-support@aster.example>",
    subject: "Scheduled maintenance tonight, 22:00 UTC",
    receivedAt: ago(380),
    spf: "PASS",
    dkim: "PASS",
    dmarc: "PASS",
    verifiedOfficial: true,
    verdict: "CLEAN",
    links: [{ url: "https://status.aster.example", verdict: "CLEAN", vtDetections: 0 }],
    attachments: [],
  },
];

let codeFindings: CodeFinding[] = [
  {
    id: "f7000001-0000-4000-8000-000000000001",
    repoFullName: "aster-research/payments-api",
    trigger: "PUSH",
    gitRef: "9c41e2b7d0a85f36c1e4b2a9d7f60c3e8b5a1d42",
    findingType: "SECRET",
    severity: "CRITICAL",
    ruleId: "aws-access-token",
    location: "config/settings.py:3",
    secretFingerprint: "3f8a1c9e0b7d42f6a5e1c3b9d8f70a2e4c6b1d9f3e5a7c0b2d4f6e8a1c3b5d7f",
    status: "OPEN",
    createdAt: ago(9),
  },
  {
    id: "f7000002-0000-4000-8000-000000000002",
    repoFullName: "aster-research/payments-api",
    trigger: "PR",
    gitRef: "4be0d1f9a2c7e3b5d8f6a0c1e9b7d3f5a2c4e6b8",
    findingType: "VULN_DEPENDENCY",
    severity: "HIGH",
    ruleId: "GHSA-p6mc-m468-83gw",
    location: "lodash@4.17.15",
    secretFingerprint: null,
    status: "OPEN",
    createdAt: ago(42),
  },
  {
    id: "f7000003-0000-4000-8000-000000000003",
    repoFullName: "aster-research/agent-release",
    trigger: "RELEASE",
    gitRef: "v2.4.0",
    findingType: "MALICIOUS_BINARY",
    severity: "CRITICAL",
    ruleId: "VT:14",
    location: "agent-linux-amd64",
    secretFingerprint: null,
    status: "OPEN",
    createdAt: ago(130),
  },
  {
    id: "f7000004-0000-4000-8000-000000000004",
    repoFullName: "aster-research/web",
    trigger: "PUSH",
    gitRef: "e2c9a7f1b3d5c8e0a4f6b2d9c1e7a3f5b8d0c2e4",
    findingType: "SECRET",
    severity: "CRITICAL",
    ruleId: "slack-webhook-url",
    location: "scripts/notify.sh:12",
    secretFingerprint: "a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90",
    status: "REVOKED",
    createdAt: ago(1500),
  },
];

let gmail: GmailConnection = {
  connected: true,
  address: "avery.lin@aster.example",
  lastPolledAt: new Date(start - 25_000).toISOString(),
};

// ---- live event stream -------------------------------------------------------

type Listener = (event: DashboardEvent) => void;
const listeners = new Set<Listener>();

function emit(event: DashboardEvent) {
  for (const listener of listeners) listener(event);
}

export function subscribe(listener: Listener): () => void {
  listeners.add(listener);
  // Pretend build-linux-01 is running: a HEARTBEAT every 10 s like the daemon.
  const timer = setInterval(() => {
    const at = new Date().toISOString();
    const daemon = agents.find((a) => a.hostname === "build-linux-01");
    if (!daemon) return;
    agents = agents.map((a) => (a.id === daemon.id ? { ...a, lastHeartbeatAt: at } : a));
    listener({ type: "HEARTBEAT", agentId: daemon.id, hostname: daemon.hostname, at });
  }, 10_000);
  return () => {
    clearInterval(timer);
    listeners.delete(listener);
  };
}

let consumerReports: ConsumerReportQueueItem[] = [
  {
    indicatorType: "DOMAIN",
    indicatorValue: "parcel-redelivery-fee.example",
    threatCategory: "PHISHING",
    status: "WAITING",
    protectedDomain: false,
    reporters: 2,
    reportersNeeded: 3,
    notes: ["Text said my parcel is held and asked for a 1.99 fee"],
    firstReportedAt: ago(140),
    lastReportedAt: ago(35),
  },
  {
    indicatorType: "URL",
    indicatorValue: "https://docs.google.com/forms/d/e/1faipqlsd-example/viewform",
    threatCategory: "PHISHING",
    status: "PROTECTED",
    protectedDomain: false,
    reporters: 1,
    reportersNeeded: 3,
    notes: ["Form asks for my bank login"],
    firstReportedAt: ago(52),
    lastReportedAt: ago(52),
  },
  {
    indicatorType: "DOMAIN",
    indicatorValue: "accounts.google.com",
    threatCategory: "MALICIOUS",
    status: "PROTECTED",
    protectedDomain: true,
    reporters: 1,
    reportersNeeded: 3,
    notes: [],
    firstReportedAt: ago(12),
    lastReportedAt: ago(12),
  },
];

let protectedDomains: string[] = ["aster.example"];

// ---- API surface ---------------------------------------------------------------

const delay = <T,>(value: T, ms = 250) =>
  new Promise<T>((resolve) => setTimeout(() => resolve(structuredClone(value)), ms));

export const mockApi = {
  session: () => delay(session),
  login: () => {
    clearMockSignedOut();
    return delay(undefined, 400);
  },
  logout: () => {
    markMockSignedOut();
    return delay(undefined);
  },
  listRules: () => delay(rules),
  listScans: () => delay(scans),
  listAgents: () => delay(agents),
  listEmails: () => delay(emails),
  gmailStatus: () => delay(gmail),
  disconnectGmail: () => {
    gmail = { connected: false, address: null, lastPolledAt: null };
    return delay(undefined);
  },

  report(req: ReportRequest) {
    return delay(upsert(req.indicatorType, req.indicatorValue, "BLACKLIST", req.threatCategory ?? "MALICIOUS", "USER_REPORT", null));
  },

  bypass(req: BypassRequest) {
    const expiresAt = req.ttlMinutes ? inMinutesFromNow(req.ttlMinutes) : null;
    return delay(upsert(req.indicatorType, req.indicatorValue, "WHITELIST", "SAFE", "USER_BYPASS", expiresAt));
  },

  listCodeFindings: () => delay(codeFindings),
  setCodeFindingStatus(id: string, status: "REVOKED" | "FALSE_POSITIVE") {
    codeFindings = codeFindings.map((f) => (f.id === id ? { ...f, status } : f));
    return delay(codeFindings.find((f) => f.id === id)!);
  },

  listConsumerReports: () => delay(consumerReports),
  approveConsumerReport(indicatorType: IndicatorType, indicatorValue: string): Promise<ThreatRule> {
    const rule: ThreatRule = {
      id: id(),
      orgId: null,
      userId: null,
      indicatorType,
      indicatorValue,
      listType: "BLACKLIST",
      threatCategory: consumerReports.find((r) => r.indicatorValue === indicatorValue)?.threatCategory ?? "MALICIOUS",
      source: "USER_REPORT",
      confidenceScore: 100,
      expiresAt: null,
      updatedAt: new Date().toISOString(),
    };
    consumerReports = consumerReports.filter((r) => r.indicatorType !== indicatorType || r.indicatorValue !== indicatorValue);
    rules = [rule, ...rules];
    return delay(rule);
  },
  dismissConsumerReport(indicatorType: IndicatorType, indicatorValue: string) {
    consumerReports = consumerReports.filter((r) => r.indicatorType !== indicatorType || r.indicatorValue !== indicatorValue);
    return delay(undefined);
  },
  listProtectedDomains: () => delay(protectedDomains),
  addProtectedDomain(domain: string) {
    protectedDomains = [...new Set([...protectedDomains, domain.trim().toLowerCase()])].sort();
    return delay(protectedDomains);
  },
  removeProtectedDomain(domain: string) {
    protectedDomains = protectedDomains.filter((d) => d !== domain);
    return delay(undefined);
  },

  registerAgent(hostname: string): Promise<RegisteredAgent> {
    const agent: EndpointAgent = { id: id(), hostname, lastHeartbeatAt: null, createdAt: new Date().toISOString() };
    agents = [agent, ...agents];
    const token = `obs_${Array.from(crypto.getRandomValues(new Uint8Array(24)), (b) => b.toString(16).padStart(2, "0")).join("")}`;
    return delay({ ...agent, token }, 400);
  },
};

function inMinutesFromNow(minutes: number) {
  return new Date(Date.now() + minutes * 60_000).toISOString();
}

// Mirrors RuleService: one row per (scope, indicator); Report/Bypass flip list_type.
function upsert(
  indicatorType: ThreatRule["indicatorType"],
  rawValue: string,
  listType: ThreatRule["listType"],
  threatCategory: ThreatRule["threatCategory"],
  source: ThreatRule["source"],
  expiresAt: string | null,
): ThreatRule {
  const indicatorValue = rawValue.trim().toLowerCase();
  const now = new Date().toISOString();
  const existing = rules.find(
    (r) => r.orgId === ORG_ID && r.userId === null && r.indicatorType === indicatorType && r.indicatorValue === indicatorValue,
  );
  const rule: ThreatRule = existing
    ? { ...existing, listType, threatCategory, source, expiresAt, updatedAt: now }
    : { id: id(), orgId: ORG_ID, userId: null, indicatorType, indicatorValue, listType, threatCategory, source, confidenceScore: 100, expiresAt, updatedAt: now };
  rules = [rule, ...rules.filter((r) => r.id !== rule.id)];
  if (indicatorType === "IP" || indicatorType === "DOMAIN") {
    emit({ type: listType === "BLACKLIST" ? "BLOCK" : "BYPASS", ruleId: rule.id, indicator_type: indicatorType, value: indicatorValue, at: now });
  }
  scans = scans.map((s) =>
    s.indicatorChecked?.toLowerCase().includes(indicatorValue) && listType === "WHITELIST" ? { ...s, verdict: "BYPASSED" } : s,
  );
  return rule;
}
