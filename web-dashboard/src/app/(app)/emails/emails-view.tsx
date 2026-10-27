"use client";

import Image from "next/image";
import Link from "next/link";
import { useEffect, useMemo, useRef, useState } from "react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { ContextNotice, MetaList, PageFootnote, PageHeading, Panel, PanelTitle } from "@/components/obsidian/panel";
import { ConfirmRuleChange, type RuleChange } from "@/components/obsidian/rule-dialogs";
import { EmptyState, ErrorNotice, LoadingRows, TablePanel } from "@/components/obsidian/table-panel";
import { AuthCheckBadge, VerdictBadge, VerdictText } from "@/components/obsidian/verdict";
import { useLive, useNow } from "@/components/shell/live-provider";
import { useResource } from "@/hooks/use-resource";
import { useLiveRules } from "@/hooks/use-live-data";
import { api, API_MODE } from "@/lib/api";
import { dateLongUtc, defang, hostOf, plural, relativeTime, shortId, timeUtc } from "@/lib/format";
import type { IndicatorType, ScannedEmail, ThreatRule, Verdict } from "@/lib/types";
import { cn } from "@/lib/utils";

type AuthFilter = "all" | "verified" | "failed";
type TimeFilter = "day" | "week" | "all";

const addressOf = (from: string) => (from.match(/<([^>]+)>/)?.[1] ?? from).trim().toLowerCase();
const authFailed = (e: ScannedEmail) => e.spf === "FAIL" || e.dkim === "FAIL" || e.dmarc === "FAIL";

function authSummary(email: ScannedEmail) {
  if (email.verifiedOfficial) return { label: "Verified official", tone: "text-verified" };
  if (authFailed(email)) return { label: "Failed checks", tone: "text-critical" };
  if (email.spf === "PASS" && email.dkim === "PASS" && email.dmarc === "PASS") {
    return { label: "Passes · not allowlisted", tone: "text-ink" };
  }
  return { label: "Incomplete", tone: "text-warning" };
}

function contentSummary(email: ScannedEmail) {
  const parts: string[] = [];
  if (email.links.length) {
    const flagged = email.links.filter((l) => l.verdict === "MALICIOUS" || l.verdict === "SUSPICIOUS").length;
    parts.push(flagged ? `${plural(flagged, "flagged link")}` : plural(email.links.length, "link"));
  }
  if (email.attachments.length) parts.push(plural(email.attachments.length, "attachment"));
  return parts.join(" · ") || "No links or attachments";
}

function topDetections(email: ScannedEmail) {
  const all = [...email.links.map((l) => l.vtDetections), ...email.attachments.map((a) => a.vtDetections)];
  return all.length ? Math.max(...all) : 0;
}

export function EmailsView() {
  const emails = useResource(api.listEmails);
  const gmail = useResource(api.gmailStatus);
  const rules = useLiveRules();
  const { session } = useLive();
  const now = useNow();
  const searchRef = useRef<HTMLInputElement>(null);

  const [query, setQuery] = useState("");
  const [verdict, setVerdict] = useState<Verdict | "all">("all");
  const [auth, setAuth] = useState<AuthFilter>("all");
  const [time, setTime] = useState<TimeFilter>("day");
  const [selectedId, setSelectedId] = useState<string | null>(null);

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === "k") {
        event.preventDefault();
        searchRef.current?.focus();
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, []);

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    const since = time === "all" ? 0 : now - (time === "day" ? 1 : 7) * 86_400_000;
    return (emails.data ?? [])
      .filter((e) => new Date(e.receivedAt).getTime() >= since)
      .filter((e) => verdict === "all" || e.verdict === verdict)
      .filter((e) => auth === "all" || (auth === "verified" ? e.verifiedOfficial : authFailed(e)))
      .filter((e) => !q || e.from.toLowerCase().includes(q) || e.subject.toLowerCase().includes(q));
  }, [emails.data, query, verdict, auth, time, now]);

  const selected = filtered.find((e) => e.id === selectedId) ?? filtered[0] ?? null;
  const filtersActive = query !== "" || verdict !== "all" || auth !== "all" || time !== "day";

  return (
    <>
      <PageHeading
        title="Email security"
        subtitle="Gmail messages scanned for sender authentication, links and attachments · Report or bypass in one click"
      />

      <GmailNotice gmail={gmail.data} error={gmail.error} now={now} />

      <div className="flex items-center gap-2.5">
        <label className="flex h-9 min-w-0 flex-1 items-center gap-[9px] rounded-md border border-line bg-surface px-3 focus-within:border-interactive focus-within:ring-1 focus-within:ring-interactive">
          <Image src="/icons/search.svg" alt="" width={16} height={16} unoptimized />
          <span className="sr-only">Search messages</span>
          <input
            ref={searchRef}
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="Search sender, domain or subject…"
            className="min-w-0 flex-1 bg-transparent text-[12px] leading-[1.4] text-ink outline-none placeholder:text-subtle focus-visible:outline-none"
          />
          <kbd className="font-sans text-[11px] text-subtle">⌘ K</kbd>
        </label>
        <Select value={auth} onValueChange={(v) => setAuth(v as AuthFilter)}>
          <SelectTrigger aria-label="Authentication">
            <SelectValue />
          </SelectTrigger>
          <SelectContent align="end">
            <SelectItem value="all">Authentication</SelectItem>
            <SelectItem value="verified">Verified official</SelectItem>
            <SelectItem value="failed">Failed checks</SelectItem>
          </SelectContent>
        </Select>
        <Select value={verdict} onValueChange={(v) => setVerdict(v as Verdict | "all")}>
          <SelectTrigger aria-label="Verdict">
            <SelectValue />
          </SelectTrigger>
          <SelectContent align="end">
            <SelectItem value="all">Verdict</SelectItem>
            <SelectItem value="MALICIOUS">Malicious</SelectItem>
            <SelectItem value="SUSPICIOUS">Suspicious</SelectItem>
            <SelectItem value="PENDING">Pending</SelectItem>
            <SelectItem value="CLEAN">Clean</SelectItem>
          </SelectContent>
        </Select>
        <Select value={time} onValueChange={(v) => setTime(v as TimeFilter)}>
          <SelectTrigger aria-label="Received">
            <SelectValue />
          </SelectTrigger>
          <SelectContent align="end">
            <SelectItem value="day">Last 24 hours</SelectItem>
            <SelectItem value="week">Last 7 days</SelectItem>
            <SelectItem value="all">All time</SelectItem>
          </SelectContent>
        </Select>
      </div>

      <TablePanel
        title="Scanned messages"
        meta={emails.data ? plural(filtered.length, "record") : undefined}
        footer={emails.data ? `Showing ${filtered.length} of ${plural(emails.data.length, "record")}` : undefined}
      >
        {emails.error ? <ErrorNotice error={emails.error} onRetry={emails.reload} /> : null}
        {emails.data === undefined && !emails.error ? (
          <LoadingRows label="Loading messages…" />
        ) : filtered.length === 0 && emails.data ? (
          <EmptyState
            title="No messages in this view"
            description={
              filtersActive
                ? `No matches${query ? ` for “${query}”` : ""}. Broaden the query or clear filters; this does not mean the inbox is safe.`
                : "Nothing has been scanned in this window yet."
            }
            onClear={
              filtersActive
                ? () => {
                    setQuery("");
                    setVerdict("all");
                    setAuth("all");
                    setTime("all");
                  }
                : undefined
            }
          />
        ) : filtered.length > 0 ? (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Event / time</TableHead>
                <TableHead>Sender / domain</TableHead>
                <TableHead>Subject</TableHead>
                <TableHead>Authentication</TableHead>
                <TableHead>Content</TableHead>
                <TableHead>VirusTotal</TableHead>
                <TableHead>Verdict</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {filtered.map((email) => {
                const authState = authSummary(email);
                const isSelected = selected?.id === email.id;
                return (
                  <TableRow
                    key={email.id}
                    data-state={isSelected ? "selected" : undefined}
                    className="cursor-pointer hover:bg-canvas/60"
                    onClick={() => setSelectedId(email.id)}
                  >
                    <TableCell>
                      <button
                        type="button"
                        className="block rounded-sm text-left font-mono text-[11px] text-ink"
                        aria-pressed={isSelected}
                        onClick={() => setSelectedId(email.id)}
                      >
                        {shortId("MAIL", email.id)}
                      </button>
                      <span className="block font-mono text-[11px] text-ink">{timeUtc(email.receivedAt)}</span>
                    </TableCell>
                    <TableCell className="max-w-[220px] break-all">{addressOf(email.from)}</TableCell>
                    <TableCell className="max-w-[220px]">{email.subject}</TableCell>
                    <TableCell>
                      <span className={cn("block", authState.tone)}>{authState.label}</span>
                      <span className="block">
                        SPF {email.spf.toLowerCase()} · DKIM {email.dkim.toLowerCase()} · DMARC {email.dmarc.toLowerCase()}
                      </span>
                    </TableCell>
                    <TableCell>{contentSummary(email)}</TableCell>
                    <TableCell>{email.verdict === "PENDING" ? "Queued" : plural(topDetections(email), "detection")}</TableCell>
                    <TableCell>
                      <VerdictText verdict={email.verdict} />
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        ) : null}
      </TablePanel>

      {selected ? <EmailReview key={selected.id} email={selected} rules={rules.data ?? []} /> : null}

      <PageFootnote>
        {API_MODE === "mock" ? "Sample records" : "Live records"} · {dateLongUtc(new Date(now))} · UTC · Obsidian
        {session?.organization ? ` / ${session.organization.name}` : ""}
      </PageFootnote>
    </>
  );
}

function GmailNotice({
  gmail,
  error,
  now,
}: {
  gmail: Awaited<ReturnType<typeof api.gmailStatus>> | undefined;
  error: Error | undefined;
  now: number;
}) {
  if (error) {
    return (
      <ContextNotice tone="critical" title="Gmail · STATUS UNAVAILABLE">
        The connection status could not be loaded. Results below may be out of date.
      </ContextNotice>
    );
  }
  if (!gmail) return null;
  return gmail.connected ? (
    <ContextNotice title="Gmail · CONNECTED">
      Polling {gmail.address} · last poll {relativeTime(gmail.lastPolledAt, now)}. Authentication results come from the topmost
      mx.google.com Authentication-Results header only.
    </ContextNotice>
  ) : (
    <ContextNotice
      tone="warning"
      title="Gmail · NOT CONNECTED"
      action={
        <Button variant="outline" size="sm" asChild>
          <Link href="/settings">Connect Gmail</Link>
        </Button>
      }
    >
      No mailbox is being polled, so no new messages are scanned. Earlier results stay listed.
    </ContextNotice>
  );
}

interface Candidate {
  key: string;
  label: string;
  type: IndicatorType;
  value: string;
}

/** Indicators a reviewer can act on: link domains first, since agents can enforce those. */
function candidatesFor(email: ScannedEmail): Candidate[] {
  const out: Candidate[] = [];
  for (const link of email.links) {
    const host = hostOf(link.url);
    if (host && !out.some((c) => c.value === host)) out.push({ key: `d:${host}`, label: "Link domain", type: "DOMAIN", value: host });
  }
  out.push({ key: "sender", label: "Sender", type: "EMAIL", value: addressOf(email.from) });
  for (const file of email.attachments) {
    out.push({ key: `h:${file.sha256}`, label: file.filename, type: "FILE_HASH", value: file.sha256 });
  }
  return out;
}

function EmailReview({ email, rules }: { email: ScannedEmail; rules: ThreatRule[] }) {
  const candidates = useMemo(() => candidatesFor(email), [email]);
  const [choice, setChoice] = useState(candidates[0].key);
  const [change, setChange] = useState<RuleChange | null>(null);
  const [lastAction, setLastAction] = useState<string | null>(null);
  const target = candidates.find((c) => c.key === choice) ?? candidates[0];
  const existing = rules.find((r) => r.indicatorType === target.type && r.indicatorValue === target.value);

  return (
    <div className="flex items-start gap-5">
      <Panel className="flex-1">
        <PanelTitle meta={timeUtc(email.receivedAt)}>{shortId("MAIL", email.id)} · Evidence</PanelTitle>
        <div className="flex flex-wrap gap-2">
          <VerdictBadge verdict={email.verdict} />
          {email.verifiedOfficial ? <Badge variant="verified">Verified official</Badge> : null}
          <AuthCheckBadge name="SPF" result={email.spf} />
          <AuthCheckBadge name="DKIM" result={email.dkim} />
          <AuthCheckBadge name="DMARC" result={email.dmarc} />
        </div>
        <MetaList
          items={[
            ["Visible sender", email.from],
            ["Subject", email.subject],
            ...email.links.map(
              (link, i): [string, React.ReactNode] => [
                email.links.length > 1 ? `Link ${i + 1}` : "Link",
                <span key={link.url}>
                  <span className="font-mono break-all">{defang(link.url)}</span>
                  {" · "}
                  <VerdictText verdict={link.verdict} />
                  {link.verdict === "PENDING" ? " · queued at VirusTotal" : ` · ${plural(link.vtDetections, "detection")}`}
                </span>,
              ],
            ),
            ...email.attachments.map(
              (file): [string, React.ReactNode] => [
                "Attachment",
                <span key={file.sha256}>
                  {file.filename} · <VerdictText verdict={file.verdict} /> · {plural(file.vtDetections, "detection")}
                  <span className="block font-mono text-[11px] break-all">sha256 {file.sha256}</span>
                </span>,
              ],
            ),
          ]}
        />
        <p className="text-[12px] leading-[1.4] text-subtle">
          Links are shown defanged. Verified official means SPF, DKIM and DMARC all pass and the sender domain is on the allowlist.
          A PENDING verdict is never treated as clean.
        </p>
      </Panel>

      <Panel className="flex-1">
        <PanelTitle>Report or bypass</PanelTitle>
        <fieldset className="flex flex-col gap-2">
          <legend className="mb-2 text-[12px] leading-[1.4] text-ink">Indicator</legend>
          {candidates.map((c) => (
            <label key={c.key} className="flex cursor-pointer items-start gap-2 text-[12px] leading-[1.4] text-subtle">
              <input
                type="radio"
                name={`indicator-${email.id}`}
                value={c.key}
                checked={choice === c.key}
                onChange={() => setChoice(c.key)}
                className="mt-0.5 accent-interactive"
              />
              <span className="min-w-0">
                <span className="text-ink">{c.label}</span> ·{" "}
                <span className="font-mono break-all">{c.type === "DOMAIN" ? defang(c.value) : c.value}</span>
              </span>
            </label>
          ))}
        </fieldset>
        <p className="text-[12px] leading-[1.4] text-subtle">
          {existing
            ? `Currently on the ${existing.listType === "BLACKLIST" ? "blacklist" : "whitelist"}.`
            : "Not on either list yet."}{" "}
          {target.type === "DOMAIN"
            ? "Domains are also enforced by every connected agent."
            : "Applies to email and message scans."}
        </p>
        <div className="flex gap-2">
          <Button
            disabled={existing?.listType === "BLACKLIST"}
            onClick={() =>
              setChange({
                action: "report",
                indicatorType: target.type,
                indicatorValue: target.value,
                currentList: existing?.listType ?? null,
                threatCategory: "PHISHING",
              })
            }
          >
            Report (Blacklist)
          </Button>
          <Button
            variant="outline"
            disabled={existing?.listType === "WHITELIST"}
            onClick={() =>
              setChange({
                action: "bypass",
                indicatorType: target.type,
                indicatorValue: target.value,
                currentList: existing?.listType ?? null,
              })
            }
          >
            Bypass (Whitelist)
          </Button>
        </div>
        <p className="text-[11px] leading-[1.4] text-subtle" role="status">
          {lastAction ?? "No action taken on this message yet."}
        </p>
      </Panel>

      {change ? (
        <ConfirmRuleChange
          change={change}
          onOpenChange={(open) => !open && setChange(null)}
          onDone={(rule) =>
            setLastAction(
              `${rule.listType === "BLACKLIST" ? "Reported" : "Bypassed"} ${rule.indicatorValue} · ${timeUtc(rule.updatedAt)}`,
            )
          }
        />
      ) : null}
    </div>
  );
}
