"use client";

import Link from "next/link";
import { Badge } from "@/components/ui/badge";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { MetaList, MetricCard, PageFootnote, PageHeading, Panel, PanelTitle } from "@/components/obsidian/panel";
import { ErrorNotice, LoadingRows, TablePanel, UnavailableNote } from "@/components/obsidian/table-panel";
import { VerdictText } from "@/components/obsidian/verdict";
import { useLive, useNow } from "@/components/shell/live-provider";
import { useLiveRules, useLiveScans } from "@/hooks/use-live-data";
import { API_MODE } from "@/lib/api";
import { channelLabel, dateLongUtc, hostOf, isOnline, plural, relativeTime, ruleSourceLabel, shortId, timeUtc } from "@/lib/format";
import type { ScanLog, ThreatRule } from "@/lib/types";
import { cn } from "@/lib/utils";

const WINDOW_HOURS = 24;
const BUCKETS = 12;

type Status = "ready" | "loading" | "error";

export function DashboardView() {
  const scans = useLiveScans();
  const rules = useLiveRules();
  const { session, agents, agentsError } = useLive();
  const now = useNow(10_000);
  const org = session?.organization?.name ?? session?.displayName;

  // Window ends at the next whole hour so the axis reads 10:00, 18:00, …
  const windowStart = Math.ceil(now / 3_600_000) * 3_600_000 - WINDOW_HOURS * 3_600_000;
  const recent = (scans.data ?? []).filter((s) => new Date(s.createdAt).getTime() >= windowStart);
  const flagged = recent.filter((s) => s.verdict !== "CLEAN");
  const malicious = flagged.filter((s) => s.verdict === "MALICIOUS");
  const pending = flagged.filter((s) => s.verdict === "PENDING");
  const blocks = (rules.data ?? []).filter((r) => r.listType === "BLACKLIST");
  const onlineAgents = (agents ?? []).filter((a) => isOnline(a.lastHeartbeatAt, now));
  const ready = scans.data !== undefined && rules.data !== undefined;
  // A failed load is shown as a failure, never as zero events or an endless spinner.
  const failure = (!scans.data && scans.error) || (!rules.data && rules.error) || undefined;
  const status: Status = ready ? "ready" : failure ? "error" : "loading";
  const retry = () => {
    scans.reload();
    rules.reload();
  };

  return (
    <>
      <PageHeading
        title="Security overview"
        subtitle={`${org ? `${org} · ` : ""}Last ${WINDOW_HOURS} hours · ${dateLongUtc(new Date(now))} · UTC`}
      />

      {ready ? (
        <div className="flex gap-4">
          <MetricCard label="Threat events" value={flagged.length} caption="Email, messaging and network" />
          <MetricCard
            label="Malicious verdicts"
            value={malicious.length}
            tone={malicious.length > 0 ? "critical" : "neutral"}
            caption={malicious[0] ? `Latest · ${latestLabel(malicious[0])}` : "None in this window"}
          />
          <MetricCard
            label="Active blocks"
            value={blocks.length}
            caption={`Enforced by ${plural(onlineAgents.length, "connected agent")}`}
          />
          <MetricCard label="Pending verdicts" value={pending.length} caption="Waiting on VirusTotal, never clean" />
        </div>
      ) : (
        <Panel className="p-0">
          {failure ? (
            <ErrorNotice error={failure} onRetry={retry} />
          ) : (
            <LoadingRows label="Loading overview… Metrics appear once records load." />
          )}
        </Panel>
      )}

      <div className="flex items-start gap-5">
        <ThreatActivity scans={flagged} windowStart={windowStart} status={status} />
        <VerdictDistribution scans={flagged} status={status} />
      </div>

      <RecentEvents scans={flagged} rules={rules.data ?? []} status={status} />

      <div className="flex items-start gap-5">
        <Panel className="flex-1">
          <PanelTitle>Connectivity &amp; enforcement</PanelTitle>
          {agents === undefined ? (
            agentsError ? (
              <UnavailableNote>Agent status is unavailable right now.</UnavailableNote>
            ) : (
              <LoadingRows label="Loading agents…" />
            )
          ) : (
            <>
              {onlineAgents.length > 0 ? (
                <Badge variant="verified">Connected</Badge>
              ) : agents.length > 0 ? (
                <Badge variant="warning">No heartbeat</Badge>
              ) : (
                <Badge>Not configured</Badge>
              )}
              <p className="text-[12px] leading-[1.4] text-subtle">
                {onlineAgents.length} of {plural(agents.length, "Linux agent")} sending heartbeats every 10 s.
              </p>
              <MetaList
                items={[
                  [
                    "Last heartbeat",
                    onlineAgents[0]
                      ? `${relativeTime(onlineAgents[0].lastHeartbeatAt, now)} · ${onlineAgents[0].hostname}`
                      : "Never / not available",
                  ],
                  ["Enforcement", onlineAgents.length > 0 ? "iptables OBSIDIAN chain + /etc/hosts" : "Unavailable"],
                ]}
              />
            </>
          )}
        </Panel>

        <Panel className="flex-1">
          <PanelTitle>Recent rule changes</PanelTitle>
          {rules.data === undefined ? (
            rules.error ? (
              <UnavailableNote>Rules are unavailable right now.</UnavailableNote>
            ) : (
              <LoadingRows label="Loading rules…" />
            )
          ) : (
            <ul className="flex flex-col gap-4 text-[12px] leading-[1.4]">
              {[...rules.data]
                .sort((a, b) => b.updatedAt.localeCompare(a.updatedAt))
                .slice(0, 3)
                .map((rule) => (
                  <li key={rule.id} className="flex flex-col gap-4">
                    <span className="break-all text-ink">
                      {rule.listType === "BLACKLIST" ? "Blocked" : "Allowed"} · {rule.indicatorValue}
                    </span>
                    <span className="-mt-3 text-subtle">
                      {ruleSourceLabel[rule.source]} · {timeUtc(rule.updatedAt)}
                      {rule.expiresAt ? ` · expires ${relativeTime(rule.expiresAt, now)}` : ""}
                    </span>
                  </li>
                ))}
            </ul>
          )}
        </Panel>

        <Panel className="flex-1">
          <PanelTitle>Quick links</PanelTitle>
          <p className="text-[12px] leading-[1.4] text-ink">Report and Bypass apply to every connected agent.</p>
          <p className="-mt-3 text-[12px] leading-[1.4] text-subtle">One report blocks the indicator across the organization.</p>
          <Link href="/firewall" className="w-fit text-[12px] leading-[1.4] text-interactive hover:underline">
            Rules and Policies → Review blocked destinations
          </Link>
          <Link href="/emails" className="w-fit text-[12px] leading-[1.4] text-interactive hover:underline">
            Email Security → Triage flagged email
          </Link>
          <Link href="/agents" className="w-fit text-[12px] leading-[1.4] text-interactive hover:underline">
            Endpoint Security → Register an agent
          </Link>
        </Panel>
      </div>

      <PageFootnote>
        {API_MODE === "mock" ? "Sample records" : "Live records"} · {dateLongUtc(new Date(now))} · UTC · Obsidian
        {org ? ` / ${org}` : ""}
      </PageFootnote>
    </>
  );
}

function ThreatActivity({ scans, windowStart, status }: { scans: ScanLog[]; windowStart: number; status: Status }) {
  const bucketMs = (WINDOW_HOURS * 3_600_000) / BUCKETS;
  const counts = Array.from({ length: BUCKETS }, () => 0);
  for (const scan of scans) {
    const index = Math.floor((new Date(scan.createdAt).getTime() - windowStart) / bucketMs);
    if (index >= 0 && index < BUCKETS) counts[index] += 1;
  }
  const max = Math.max(...counts, 1);
  const axis = [0, 4, 8, 12].map((i) => new Date(windowStart + i * bucketMs).toISOString().slice(11, 16));

  return (
    <Panel className="flex-1">
      <PanelTitle meta={API_MODE === "mock" ? "Sample data" : "Scan verdicts"}>Threat activity</PanelTitle>
      {status === "ready" ? (
        <figure className="flex flex-col gap-3" aria-label={`Flagged events per two hours: ${counts.join(", ")}`}>
          <figcaption className="flex gap-4 text-[11px] leading-[1.4]">
            <span className="flex-1 text-subtle">Flagged events · two-hour intervals · UTC</span>
            <span className="text-interactive">{plural(scans.length, "event")}</span>
          </figcaption>
          <div className="flex h-[150px] items-end gap-[22px] border-b border-line px-3">
            {counts.map((count, index) => (
              <div key={index} className="flex h-full min-w-0 flex-1 flex-col justify-end gap-2">
                <span className="text-[10px] leading-[1.4] text-subtle">{count > 0 ? count : "​"}</span>
                <div
                  className={cn("w-full rounded-[2px]", count > 0 ? "bg-interactive" : "bg-line")}
                  style={{ height: count > 0 ? Math.max(6, Math.round((count / max) * 96)) : 2 }}
                />
              </div>
            ))}
          </div>
          <div className="flex justify-between text-[10px] leading-[1.4] text-subtle">
            {axis.map((label, index) => (
              <span key={index}>{label}</span>
            ))}
          </div>
        </figure>
      ) : status === "error" ? (
        <UnavailableNote>Activity is unavailable while records can&apos;t be loaded.</UnavailableNote>
      ) : (
        <LoadingRows label="Loading activity…" />
      )}
    </Panel>
  );
}

function VerdictDistribution({ scans, status }: { scans: ScanLog[]; status: Status }) {
  const buckets = [
    { label: "Malicious", count: scans.filter((s) => s.verdict === "MALICIOUS").length, bar: "bg-critical" },
    { label: "Suspicious", count: scans.filter((s) => s.verdict === "SUSPICIOUS").length, bar: "bg-warning" },
    { label: "Pending", count: scans.filter((s) => s.verdict === "PENDING").length, bar: "bg-subtle" },
  ];
  const total = Math.max(scans.length, 1);

  return (
    <Panel className="w-80 shrink-0">
      <PanelTitle meta={status === "ready" ? plural(scans.length, "event") : undefined}>Verdict distribution</PanelTitle>
      {status === "ready" ? (
        <div className="flex flex-col gap-5">
          {buckets.map((bucket) => (
            <div key={bucket.label} className="flex flex-col gap-[7px]">
              <div className="flex justify-between text-[12px] leading-[1.4] text-ink">
                <span>{bucket.label}</span>
                <span>{bucket.count}</span>
              </div>
              <div className={cn("h-1.5 rounded-[2px]", bucket.bar)} style={{ width: `${(bucket.count / total) * 100}%` }} />
            </div>
          ))}
        </div>
      ) : status === "error" ? (
        <UnavailableNote>Verdicts are unavailable while records can&apos;t be loaded.</UnavailableNote>
      ) : (
        <LoadingRows label="Loading verdicts…" />
      )}
    </Panel>
  );
}

function RecentEvents({ scans, rules, status }: { scans: ScanLog[]; rules: ThreatRule[]; status: Status }) {
  const rows = scans.slice(0, 5);
  const loading = status !== "ready";

  return (
    <TablePanel
      title="Recent threat events"
      meta={loading ? undefined : plural(scans.length, "record")}
      footer={
        loading ? undefined : (
          <span>
            Showing {rows.length} of {plural(scans.length, "record")}
            <Link href="/threats" className="ml-5 text-interactive hover:underline">
              View all in Threat Monitoring →
            </Link>
          </span>
        )
      }
    >
      {status === "error" ? (
        <UnavailableNote className="px-4 pb-4">Events are unavailable while records can&apos;t be loaded.</UnavailableNote>
      ) : status === "loading" ? (
        <LoadingRows />
      ) : rows.length === 0 ? (
        <p className="px-4 pb-4 text-[12px] text-subtle">
          No flagged events in the last 24 hours. This does not mean the environment is secure.
        </p>
      ) : (
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
              const rule = matchingRule(scan, rules);
              return (
                <TableRow key={scan.id}>
                  <TableCell className="font-mono text-[11px] text-ink">{shortId("SCN", scan.id)}</TableCell>
                  <TableCell className="max-w-[240px] truncate" title={scan.indicatorChecked ?? undefined}>
                    {scan.indicatorChecked}
                  </TableCell>
                  <TableCell>{channelLabel[scan.channel]}</TableCell>
                  <TableCell className="max-w-[180px] truncate">{scan.sender}</TableCell>
                  <TableCell>{scan.verdict === "PENDING" ? "Queued" : plural(scan.vtPositives, "detection")}</TableCell>
                  <TableCell>
                    <VerdictText verdict={scan.verdict} />
                  </TableCell>
                  <TableCell className={rule?.listType === "WHITELIST" ? "text-warning" : undefined}>
                    {rule ? (rule.listType === "BLACKLIST" ? "Blocked" : "Bypassed") : "No rule"}
                  </TableCell>
                </TableRow>
              );
            })}
          </TableBody>
        </Table>
      )}
    </TablePanel>
  );
}

function latestLabel(scan: ScanLog) {
  const checked = scan.indicatorChecked ?? "";
  return hostOf(checked) ?? checked;
}

function matchingRule(scan: ScanLog, rules: ThreatRule[]) {
  const checked = scan.indicatorChecked?.toLowerCase() ?? "";
  const sender = scan.sender?.toLowerCase() ?? "";
  return rules.find((rule) => checked.includes(rule.indicatorValue) || sender === rule.indicatorValue);
}
