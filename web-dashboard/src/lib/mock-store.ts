// In-browser sample data used while NEXT_PUBLIC_API_MODE isn't "live". It keeps
// the same shapes as the real API so pages don't branch on the mode, and Report /
// Bypass mutate it so the demo flow works before Spring is up. Names, domains
// (.example) and addresses (TEST-NET) are illustrative.

import type {
  BypassRequest,
  DashboardEvent,
  EndpointAgent,
  GmailConnection,
  RegisteredAgent,
  ReportRequest,
  ScanLog,
  ScannedEmail,
  SessionInfo,
  ThreatRule,
} from "./types";

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

// ---- API surface ---------------------------------------------------------------

const delay = <T,>(value: T, ms = 250) =>
  new Promise<T>((resolve) => setTimeout(() => resolve(structuredClone(value)), ms));

export const mockApi = {
  session: () => delay(session),
  login: () => delay(undefined, 400),
  logout: () => delay(undefined),
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
