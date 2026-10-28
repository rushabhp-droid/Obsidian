"use client";

import Image from "next/image";
import { useEffect, useMemo, useRef, useState } from "react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { ContextNotice, MetaList, PageFootnote, PageHeading, Panel, PanelTitle } from "@/components/obsidian/panel";
import { ConfirmRuleChange, type RuleChange } from "@/components/obsidian/rule-dialogs";
import { EmptyState, ErrorNotice, LoadingRows } from "@/components/obsidian/table-panel";
import { VerdictBadge, VerdictText } from "@/components/obsidian/verdict";
import { useLive, useNow } from "@/components/shell/live-provider";
import { useLiveRules, useLiveScans } from "@/hooks/use-live-data";
import { API_MODE } from "@/lib/api";
import { channelLabel, dateLongUtc, defang, hostOf, indicatorTypeLabel, plural, relativeTime, shortId, timeUtc } from "@/lib/format";
import { validateIndicator } from "@/lib/indicators";
import type { Channel, IndicatorType, ScanLog, ScanVerdict, ThreatRule } from "@/lib/types";
import { cn } from "@/lib/utils";

type VerdictTab = "all" | Exclude<ScanVerdict, "CLEAN">;
type TimeFilter = "day" | "week" | "all";
const PAGE_SIZE = 10;
const TABS: VerdictTab[] = ["all", "MALICIOUS", "SUSPICIOUS", "PENDING", "BYPASSED"];
const tabLabel: Record<VerdictTab, string> = {
  all: "All events",
  MALICIOUS: "Malicious",
  SUSPICIOUS: "Suspicious",
  PENDING: "Pending",
  BYPASSED: "Bypassed",
};

interface Candidate {
  key: string;
  label: string;
  type: IndicatorType;
  value: string;
}

/** Indicators in a scan that a reviewer can report or bypass. */
function candidatesFor(scan: ScanLog): Candidate[] {
  const out: Candidate[] = [];
  const checked = scan.indicatorChecked?.trim().toLowerCase() ?? "";
  const host = /^https?:\/\//.test(checked) ? hostOf(checked) : null;
  if (host) out.push({ key: "domain", label: "Domain", type: "DOMAIN", value: host });
  const kinds: IndicatorType[] = ["URL", "IP", "FILE_HASH", "DOMAIN"];
  const kind = kinds.find((t) => validateIndicator(t, checked) === null);
  if (kind && !(kind === "DOMAIN" && host === checked)) {
    out.push({ key: "checked", label: indicatorTypeLabel[kind], type: kind, value: checked });
  }
  const sender = scan.sender?.trim().toLowerCase() ?? "";
  if (validateIndicator("EMAIL", sender) === null) out.push({ key: "sender", label: "Sender", type: "EMAIL", value: sender });
  else if (validateIndicator("PHONE", sender) === null) out.push({ key: "sender", label: "Sender", type: "PHONE", value: sender });
  return out;
}

function ruleFor(scan: ScanLog, rules: ThreatRule[]) {
  const checked = scan.indicatorChecked?.toLowerCase() ?? "";
  const sender = scan.sender?.toLowerCase() ?? "";
  return rules.find((r) => checked.includes(r.indicatorValue) || sender === r.indicatorValue);
}

const MESSAGING: Channel[] = ["SMS", "WHATSAPP"];

/**
 * The scan feed. "messaging" is the Messaging Security page: SMS and WhatsApp
 * only. Employees see their own scans (the API scopes them), so the wording says "your".
 */
export function ThreatsView({ variant = "all" }: { variant?: "all" | "messaging" }) {
  const scans = useLiveScans();
  const rules = useLiveRules();
  const { session, socketConnected } = useLive();
  const now = useNow();
  const searchRef = useRef<HTMLInputElement>(null);

  const [tab, setTab] = useState<VerdictTab>("all");
  const [query, setQuery] = useState("");
  const [channel, setChannel] = useState<Channel | "all">("all");
  const [time, setTime] = useState<TimeFilter>("day");
  const [page, setPage] = useState(0);
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

  // Clean scans aren't threat events; the feed lists everything else.
  const inWindow = useMemo(() => {
    const since = time === "all" ? 0 : now - (time === "day" ? 1 : 7) * 86_400_000;
    return (scans.data ?? []).filter(
      (s) =>
        s.verdict !== "CLEAN" &&
        new Date(s.createdAt).getTime() >= since &&
        (variant === "all" || MESSAGING.includes(s.channel)),
    );
  }, [scans.data, time, now, variant]);
  const channels = variant === "messaging" ? MESSAGING : (Object.keys(channelLabel) as Channel[]);
  const own = session?.role !== "ADMIN";

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    return inWindow
      .filter((s) => tab === "all" || s.verdict === tab)
      .filter((s) => channel === "all" || s.channel === channel)
      .filter(
        (s) =>
          !q ||
          s.indicatorChecked?.toLowerCase().includes(q) ||
          s.sender?.toLowerCase().includes(q) ||
          shortId("SCN", s.id).toLowerCase().includes(q),
      );
  }, [inWindow, tab, channel, query]);

  const pageCount = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE));
  const currentPage = Math.min(page, pageCount - 1);
  const rows = filtered.slice(currentPage * PAGE_SIZE, (currentPage + 1) * PAGE_SIZE);
  const selected = filtered.find((s) => s.id === selectedId) ?? filtered[0] ?? null;
  const count = (verdict: VerdictTab) => (verdict === "all" ? inWindow.length : inWindow.filter((s) => s.verdict === verdict).length);
  const filtersActive = query !== "" || channel !== "all" || time !== "day" || tab !== "all";

  return (
    <>
      <PageHeading
        title={variant === "messaging" ? "Messaging security" : own ? "My threat events" : "Threat monitoring"}
        subtitle={`${
          variant === "messaging"
            ? own
              ? "SMS and WhatsApp messages scanned on your phone"
              : "SMS and WhatsApp scans from enrolled phones"
            : own
              ? "Email, SMS and WhatsApp scans for your account"
              : "Unified feed across email, SMS, WhatsApp and network"
        } · ${plural(inWindow.length, "event")} · ${
          time === "day" ? "last 24 hours" : time === "week" ? "last 7 days" : "all time"
        }`}
        actions={
          socketConnected ? <Badge variant="verified">Live feed connected</Badge> : <Badge variant="warning">Live feed reconnecting</Badge>
        }
      />

      <Tabs
        value={tab}
        onValueChange={(v) => {
          setTab(v as VerdictTab);
          setPage(0);
        }}
      >
        <TabsList aria-label="Verdict">
          {TABS.map((t) => (
            <TabsTrigger key={t} value={t}>
              {tabLabel[t]} · {count(t)}
            </TabsTrigger>
          ))}
        </TabsList>
      </Tabs>

      <div className="flex items-center gap-2.5">
        <label className="flex h-9 min-w-0 flex-1 items-center gap-[9px] rounded-md border border-line bg-surface px-3 focus-within:border-interactive focus-within:ring-1 focus-within:ring-interactive">
          <Image src="/icons/search.svg" alt="" width={16} height={16} unoptimized />
          <span className="sr-only">Search events</span>
          <input
            ref={searchRef}
            value={query}
            onChange={(event) => {
              setQuery(event.target.value);
              setPage(0);
            }}
            placeholder="Search event, indicator or sender…"
            className="min-w-0 flex-1 bg-transparent text-[12px] leading-[1.4] text-ink outline-none placeholder:text-subtle focus-visible:outline-none"
          />
          <kbd className="font-sans text-[11px] text-subtle">⌘ K</kbd>
        </label>
        <Select
          value={channel}
          onValueChange={(v) => {
            setChannel(v as Channel | "all");
            setPage(0);
          }}
        >
          <SelectTrigger aria-label="Source">
            <SelectValue />
          </SelectTrigger>
          <SelectContent align="end">
            <SelectItem value="all">Source</SelectItem>
            {channels.map((c) => (
              <SelectItem key={c} value={c}>
                {channelLabel[c]}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Select
          value={time}
          onValueChange={(v) => {
            setTime(v as TimeFilter);
            setPage(0);
          }}
        >
          <SelectTrigger aria-label="Time range">
            <SelectValue />
          </SelectTrigger>
          <SelectContent align="end">
            <SelectItem value="day">Last 24 hours</SelectItem>
            <SelectItem value="week">Last 7 days</SelectItem>
            <SelectItem value="all">All time</SelectItem>
          </SelectContent>
        </Select>
      </div>

      <section className="flex min-w-0 flex-col rounded-lg border border-line bg-surface" aria-label="Threat events">
        {scans.error ? <ErrorNotice error={scans.error} onRetry={scans.reload} /> : null}
        {scans.data === undefined && !scans.error ? (
          <LoadingRows label="Loading events…" />
        ) : rows.length === 0 && scans.data ? (
          <EmptyState
            title="No events in this view"
            description={
              filtersActive
                ? `No matches${query ? ` for “${query}”` : ""}. Broaden the query or clear filters; this does not mean the environment is secure.`
                : "Nothing has been flagged in this window. This does not mean the environment is secure."
            }
            onClear={
              filtersActive
                ? () => {
                    setQuery("");
                    setChannel("all");
                    setTime("day");
                    setTab("all");
                  }
                : undefined
            }
          />
        ) : rows.length > 0 ? (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Event</TableHead>
                <TableHead>Indicator</TableHead>
                <TableHead>Source</TableHead>
                <TableHead>Sender</TableHead>
                <TableHead>VirusTotal</TableHead>
                <TableHead>Verdict</TableHead>
                <TableHead>Rule</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {rows.map((scan) => {
                const rule = ruleFor(scan, rules.data ?? []);
                const isSelected = selected?.id === scan.id;
                return (
                  <TableRow
                    key={scan.id}
                    data-state={isSelected ? "selected" : undefined}
                    className="cursor-pointer hover:bg-canvas/60"
                    onClick={() => setSelectedId(scan.id)}
                  >
                    <TableCell>
                      <button
                        type="button"
                        className="block rounded-sm text-left font-mono text-[11px] text-ink"
                        aria-pressed={isSelected}
                        onClick={() => setSelectedId(scan.id)}
                      >
                        {shortId("SCN", scan.id)}
                      </button>
                      <span className="block">{timeUtc(scan.createdAt)}</span>
                    </TableCell>
                    <TableCell className="max-w-[260px] truncate" title={scan.indicatorChecked ?? undefined}>
                      {scan.indicatorChecked}
                    </TableCell>
                    <TableCell>{channelLabel[scan.channel]}</TableCell>
                    <TableCell className="max-w-[200px] truncate">{scan.sender}</TableCell>
                    <TableCell>{scan.verdict === "PENDING" ? "Queued" : plural(scan.vtPositives, "detection")}</TableCell>
                    <TableCell>
                      <VerdictText verdict={scan.verdict} />
                    </TableCell>
                    <TableCell className={cn(rule?.listType === "WHITELIST" && "text-warning")}>
                      {rule ? (rule.listType === "BLACKLIST" ? "Blocked" : "Bypassed") : "No rule"}
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        ) : null}
        {scans.data ? (
          <div className="flex items-center gap-5 px-4 py-[13px] text-[11px] text-subtle">
            <span>
              Showing {rows.length} of {plural(filtered.length, "record")}
            </span>
            <Button variant="link" className="text-[11px]" disabled={currentPage === 0} onClick={() => setPage(currentPage - 1)}>
              Previous
            </Button>
            <span className="text-interactive">{currentPage + 1}</span>
            <Button
              variant="link"
              className="text-[11px]"
              disabled={currentPage >= pageCount - 1}
              onClick={() => setPage(currentPage + 1)}
            >
              Next
            </Button>
          </div>
        ) : null}
      </section>

      <div className="flex items-start gap-5">
        {selected ? (
          <SelectedEvent key={selected.id} scan={selected} rules={rules.data ?? []} now={now} />
        ) : (
          <Panel className="flex-1">
            <PanelTitle>Selected event</PanelTitle>
            <p className="text-[12px] text-subtle">Select an event to report or bypass its indicator.</p>
          </Panel>
        )}
        <Panel className="flex-1">
          <PanelTitle>How verdicts are decided</PanelTitle>
          <p className="text-[13px] leading-[1.4] text-ink">Rules → VirusTotal cache (24 h) → VirusTotal v3</p>
          <p className="text-[12px] leading-[1.4] text-subtle">
            A matching blacklist or whitelist rule decides first. Otherwise 0 detections is clean, 1–2 is suspicious and 3 or more
            is malicious. Unknown URLs are submitted and polled; until a result arrives, or when the daily quota runs out, the
            verdict is PENDING, never clean.
          </p>
          <ContextNotice title="Live feed">
            New verdicts and rule changes arrive over /ws/dashboard as they happen; there is no need to refresh.
          </ContextNotice>
        </Panel>
      </div>

      <PageFootnote>
        {API_MODE === "mock" ? "Sample records" : "Live records"} · {dateLongUtc(new Date(now))} · UTC · Obsidian
        {session?.organization ? ` / ${session.organization.name}` : ""}
      </PageFootnote>
    </>
  );
}

function SelectedEvent({ scan, rules, now }: { scan: ScanLog; rules: ThreatRule[]; now: number }) {
  const candidates = useMemo(() => candidatesFor(scan), [scan]);
  const [choice, setChoice] = useState(candidates[0]?.key ?? "");
  const [change, setChange] = useState<RuleChange | null>(null);
  const [lastAction, setLastAction] = useState<string | null>(null);
  const target = candidates.find((c) => c.key === choice) ?? candidates[0];
  const existing = target ? rules.find((r) => r.indicatorType === target.type && r.indicatorValue === target.value) : undefined;

  return (
    <Panel className="flex-1">
      <PanelTitle meta={relativeTime(scan.createdAt, now)}>{shortId("SCN", scan.id)} · Selected event</PanelTitle>
      <div className="flex gap-2">
        <VerdictBadge verdict={scan.verdict} />
        <Badge>{channelLabel[scan.channel]}</Badge>
      </div>
      <MetaList
        items={[
          ["Indicator", <span key="i" className="font-mono break-all">{defang(scan.indicatorChecked ?? "–")}</span>],
          ["Sender", scan.sender ?? "–"],
          ["VirusTotal", scan.verdict === "PENDING" ? "Queued · not yet analyzed" : plural(scan.vtPositives, "detection")],
        ]}
      />
      {target ? (
        <>
          <fieldset className="flex flex-col gap-2">
            <legend className="mb-2 text-[12px] leading-[1.4] text-ink">Act on</legend>
            {candidates.map((c) => (
              <label key={c.key} className="flex cursor-pointer items-start gap-2 text-[12px] leading-[1.4] text-subtle">
                <input
                  type="radio"
                  name={`candidate-${scan.id}`}
                  checked={choice === c.key}
                  onChange={() => setChoice(c.key)}
                  className="mt-0.5 accent-interactive"
                />
                <span className="min-w-0">
                  <span className="text-ink">{c.label}</span> · <span className="font-mono break-all">{c.value}</span>
                </span>
              </label>
            ))}
          </fieldset>
          <div className="flex gap-2">
            <Button
              disabled={existing?.listType === "BLACKLIST"}
              onClick={() =>
                setChange({ action: "report", indicatorType: target.type, indicatorValue: target.value, currentList: existing?.listType ?? null })
              }
            >
              Report (Blacklist)
            </Button>
            <Button
              variant="outline"
              disabled={existing?.listType === "WHITELIST"}
              onClick={() =>
                setChange({ action: "bypass", indicatorType: target.type, indicatorValue: target.value, currentList: existing?.listType ?? null })
              }
            >
              Bypass (Whitelist)
            </Button>
          </div>
          <p className="text-[11px] leading-[1.4] text-subtle" role="status">
            {lastAction ??
              (existing
                ? `Currently on the ${existing.listType === "BLACKLIST" ? "blacklist" : "whitelist"}.`
                : "Not on either list yet.")}
          </p>
        </>
      ) : (
        <p className="text-[12px] text-subtle">This event has no indicator that can be listed.</p>
      )}
      {change ? (
        <ConfirmRuleChange
          change={change}
          onOpenChange={(open) => !open && setChange(null)}
          onDone={(rule) =>
            setLastAction(`${rule.listType === "BLACKLIST" ? "Reported" : "Bypassed"} ${rule.indicatorValue} · ${timeUtc(rule.updatedAt)}`)
          }
        />
      ) : null}
    </Panel>
  );
}
