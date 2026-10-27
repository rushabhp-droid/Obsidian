import type { Channel, IndicatorType, RuleSource, ScanVerdict } from "./types";

export type Tone = "neutral" | "critical" | "warning" | "verified" | "info";

// Color always comes with the verdict's own label. Green is reserved for
// verified states, so CLEAN stays neutral: no findings is not a safety claim.
export const verdictTone: Record<ScanVerdict, Tone> = {
  MALICIOUS: "critical",
  SUSPICIOUS: "warning",
  PENDING: "warning",
  CLEAN: "neutral",
  BYPASSED: "info",
};

export const toneText: Record<Tone, string> = {
  neutral: "text-subtle",
  critical: "text-critical",
  warning: "text-warning",
  verified: "text-verified",
  info: "text-interactive",
};

export const channelLabel: Record<Channel, string> = {
  EMAIL: "Email",
  SMS: "SMS",
  WHATSAPP: "WhatsApp",
  FIREWALL: "Network",
};

export const indicatorTypeLabel: Record<IndicatorType, string> = {
  DOMAIN: "Domain",
  URL: "URL",
  IP: "IP address",
  EMAIL: "Email sender",
  PHONE: "Phone number",
  FILE_HASH: "File hash",
};

export const ruleSourceLabel: Record<RuleSource, string> = {
  VIRUSTOTAL: "VirusTotal",
  USER_REPORT: "User report",
  USER_BYPASS: "User bypass",
  ADMIN_POLICY: "Admin policy",
};

/** Daemons heartbeat every 10 s; three missed beats means offline. */
export const HEARTBEAT_STALE_MS = 30_000;

export function isOnline(lastHeartbeatAt: string | null, now = Date.now()) {
  return lastHeartbeatAt !== null && now - new Date(lastHeartbeatAt).getTime() < HEARTBEAT_STALE_MS;
}

export function shortId(prefix: string, uuid: string) {
  return `${prefix}-${uuid.replace(/-/g, "").slice(-4).toUpperCase()}`;
}

export function timeUtc(iso: string) {
  return `${new Date(iso).toISOString().slice(11, 16)} UTC`;
}

export function dateLongUtc(date: Date) {
  return date.toLocaleDateString("en-GB", { day: "numeric", month: "long", year: "numeric", timeZone: "UTC" });
}

export function relativeTime(iso: string | null, now = Date.now()) {
  if (!iso) return "Never";
  const seconds = Math.round((now - new Date(iso).getTime()) / 1000);
  // `now` ticks every few seconds, so a record written a moment ago can look slightly in the future.
  if (seconds > -10 && seconds < 5) return "just now";
  if (seconds < 0) return relativeFuture(-seconds);
  if (seconds < 60) return `${seconds} s ago`;
  const minutes = Math.round(seconds / 60);
  if (minutes < 60) return `${minutes} min ago`;
  const hours = Math.round(minutes / 60);
  if (hours < 48) return `${hours} h ago`;
  return `${Math.round(hours / 24)} d ago`;
}

function relativeFuture(seconds: number) {
  if (seconds < 60) return `in ${seconds} s`;
  const minutes = Math.round(seconds / 60);
  if (minutes < 60) return `in ${minutes} min`;
  return `in ${Math.round(minutes / 60)} h`;
}

export function plural(count: number, one: string, many = `${one}s`) {
  return `${count} ${count === 1 ? one : many}`;
}

/** Defang a URL or host for evidence views so it can't be clicked or auto-linked. */
export function defang(value: string) {
  return value.replace(/^http(s?):\/\//i, "hxxp$1://").replace(/\./g, "[.]");
}

export function hostOf(url: string) {
  try {
    return new URL(url).hostname.toLowerCase();
  } catch {
    return null;
  }
}
