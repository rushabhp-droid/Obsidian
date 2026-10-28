// Fetch wrapper for /api/v1. Requests stay same-origin:
// the tunnel, or next.config.ts rewrites on localhost, route /api to Spring and the
// __Host-session cookie rides along. Until Spring is up, NEXT_PUBLIC_API_MODE
// defaults to "mock" and every call is served by mock-store.ts.

import { ApiError } from "./api-error";
import { mockApi } from "./mock-store";
import type {
  BypassRequest,
  CodeFinding,
  AddMemberRequest,
  AgentDetail,
  AuditDetail,
  AuditEntry,
  ConsumerReportQueueItem,
  EndpointAgent,
  GmailConnection,
  IndicatorType,
  Member,
  OrgSettings,
  RegisteredAgent,
  ReportSummary,
  Role,
  ReportRequest,
  ScanLog,
  ScannedEmail,
  SessionInfo,
  ThreatRule,
} from "./types";

export const API_MODE: "live" | "mock" =
  process.env.NEXT_PUBLIC_API_MODE === "live" ? "live" : "mock";

export { ApiError } from "./api-error";

// Spring's CookieCsrfTokenRepository convention; harmless when the cookie is absent.
export function csrfHeaders(): Record<string, string> {
  if (typeof document === "undefined") return {};
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
  return match ? { "X-XSRF-TOKEN": decodeURIComponent(match[1]) } : {};
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const response = await fetch(path, {
    ...init,
    credentials: "same-origin",
    headers: {
      Accept: "application/json",
      ...(init.body ? { "Content-Type": "application/json" } : {}),
      ...csrfHeaders(),
      ...init.headers,
    },
  });
  if (!response.ok) {
    throw new ApiError(response.status, await errorMessage(response));
  }
  if (response.status === 204 || response.headers.get("content-length") === "0") {
    return undefined as T;
  }
  return (await response.json()) as T;
}

// Spring answers with RFC 7807 problem details (docs/protocol.md); show their `detail`.
async function errorMessage(response: Response) {
  const text = await response.text().catch(() => "");
  try {
    const problem = JSON.parse(text) as { detail?: unknown; title?: unknown };
    if (typeof problem.detail === "string" && problem.detail) return problem.detail;
    if (typeof problem.title === "string" && problem.title) return problem.title;
  } catch {
    // Not JSON: fall through to the raw text.
  }
  return text || response.statusText || `Request failed (${response.status})`;
}

const get = <T>(path: string) => request<T>(path);
const post = <T>(path: string, body?: unknown, headers?: HeadersInit) =>
  request<T>(path, { method: "POST", body: body === undefined ? undefined : JSON.stringify(body), headers });

const del = <T>(path: string) => request<T>(path, { method: "DELETE" });
const put = <T>(path: string, body: unknown) => request<T>(path, { method: "PUT", body: JSON.stringify(body) });

const live = {
  // Auth and rule mutation requests follow docs/protocol.md.
  login: (email: string, password: string, turnstileToken: string | null) =>
    post<void>("/api/v1/auth/login", { email, password, turnstileToken }),
  listRules: () => get<ThreatRule[]>("/api/v1/rules"),
  report: (req: ReportRequest) => post<ThreatRule>("/api/v1/rules/report", req),
  bypass: (req: BypassRequest) => post<ThreatRule>("/api/v1/rules/bypass", req),
  registerAgent: (hostname: string) => post<RegisteredAgent>("/api/v1/agents", { hostname }),

  // Read endpoints used by the dashboard pages.
  session: () => get<SessionInfo>("/api/v1/auth/me"),
  logout: () => post<void>("/api/v1/auth/logout"),
  listScans: () => get<ScanLog[]>("/api/v1/scans"),
  listAgents: () => get<EndpointAgent[]>("/api/v1/agents"),
  listEmails: () => get<ScannedEmail[]>("/api/v1/emails"),
  gmailStatus: () => get<GmailConnection>("/api/v1/integrations/gmail"),
  disconnectGmail: () => post<void>("/api/v1/integrations/gmail/disconnect"),

  // Code Guard [P1], admins only.
  listCodeFindings: () => get<CodeFinding[]>("/api/v1/code/findings"),
  setCodeFindingStatus: (id: string, status: "REVOKED" | "FALSE_POSITIVE") =>
    post<CodeFinding>(`/api/v1/code/findings/${encodeURIComponent(id)}/status`, { status }),

  // Reports from the B2C app, admins only. Approving blocks the indicator globally.
  listConsumerReports: () => get<ConsumerReportQueueItem[]>("/api/v1/consumer-reports"),
  approveConsumerReport: (indicatorType: IndicatorType, indicatorValue: string) =>
    post<ThreatRule>("/api/v1/consumer-reports/approve", { indicatorType, indicatorValue }),
  dismissConsumerReport: (indicatorType: IndicatorType, indicatorValue: string) =>
    post<void>("/api/v1/consumer-reports/dismiss", { indicatorType, indicatorValue }),
  listProtectedDomains: () => get<string[]>("/api/v1/protected-domains"),
  addProtectedDomain: (domain: string) => post<string[]>("/api/v1/protected-domains", { domain }),
  removeProtectedDomain: (domain: string) => del<void>(`/api/v1/protected-domains/${encodeURIComponent(domain)}`),

  // Organization admins: settings, members, audit log, endpoint detail.
  orgSettings: () => get<OrgSettings>("/api/v1/org"),
  updateOrg: (update: { name?: string; policyAllowUserBypass?: boolean }) => put<OrgSettings>("/api/v1/org", update),
  listMembers: () => get<Member[]>("/api/v1/org/members"),
  addMember: (req: AddMemberRequest) => post<Member>("/api/v1/org/members", req),
  setMemberRole: (id: string, role: Exclude<Role, "CONSUMER">) =>
    put<Member>(`/api/v1/org/members/${encodeURIComponent(id)}/role`, { role }),
  removeMember: (id: string) => del<void>(`/api/v1/org/members/${encodeURIComponent(id)}`),
  listAudit: (limit = 200) => get<AuditEntry[]>(`/api/v1/audit?limit=${limit}`),
  auditDetail: (id: string) => get<AuditDetail>(`/api/v1/audit/${encodeURIComponent(id)}`),
  agentDetail: (id: string) => get<AgentDetail>(`/api/v1/agents/${encodeURIComponent(id)}`),

  // Everyone: their organization's (admins) or their own (employees) scan totals.
  reportSummary: (days: number) => get<ReportSummary>(`/api/v1/reports/summary?days=${days}`),
};

export const api: typeof live = API_MODE === "live" ? live : mockApi;

/** Spring OAuth2 client entry point for Gmail (routed to Spring, not Next.js). */
export const GMAIL_CONNECT_URL = "/oauth2/authorization/google";
