// Fetch wrapper for /api/v1. Requests stay same-origin:
// the tunnel, or next.config.ts rewrites on localhost, route /api to Spring and the
// __Host-session cookie rides along. Until Spring is up, NEXT_PUBLIC_API_MODE
// defaults to "mock" and every call is served by mock-store.ts.

import { mockApi } from "./mock-store";
import type {
  BypassRequest,
  CodeFinding,
  EndpointAgent,
  GmailConnection,
  RegisteredAgent,
  ReportRequest,
  ScanLog,
  ScannedEmail,
  SessionInfo,
  ThreatRule,
} from "./types";

export const API_MODE: "live" | "mock" =
  process.env.NEXT_PUBLIC_API_MODE === "live" ? "live" : "mock";

export class ApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
  ) {
    super(message);
  }
}

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
};

export const api: typeof live = API_MODE === "live" ? live : mockApi;

/** Spring OAuth2 client entry point for Gmail (routed to Spring, not Next.js). */
export const GMAIL_CONNECT_URL = "/oauth2/authorization/google";
