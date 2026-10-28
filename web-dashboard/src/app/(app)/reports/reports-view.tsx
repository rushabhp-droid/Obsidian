"use client";

import { useCallback, useState } from "react";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { MetaList, MetricCard, PageFootnote, PageHeading, Panel, PanelTitle } from "@/components/obsidian/panel";
import { ErrorNotice, LoadingRows, TablePanel } from "@/components/obsidian/table-panel";
import { useLive, useNow } from "@/components/shell/live-provider";
import { useResource } from "@/hooks/use-resource";
import { api, API_MODE } from "@/lib/api";
import { channelLabel, dateLongUtc, plural } from "@/lib/format";
import type { Channel, ReportCounts, ReportSummary } from "@/lib/types";
import { cn } from "@/lib/utils";

const RANGES = [1, 7, 30] as const;
const sourceLabel = (channel: Channel | "CODE") => (channel === "CODE" ? "Code" : channelLabel[channel]);
const flagged = (c: ReportCounts) => c.malicious + c.suspicious + c.pending;

/** Scan verdict trends and the rules in force: the organization's for admins, your own for employees. */
export function ReportsView() {
  const [days, setDays] = useState<(typeof RANGES)[number]>(7);
  const load = useCallback(() => api.reportSummary(days), [days]);
  const summary = useResource(load);
  const { session } = useLive();
  const now = useNow(60_000);
  const own = session?.role !== "ADMIN";
  const data = summary.data;

  return (
    <>
      <PageHeading
        title={own ? "My reports" : "Reports"}
        subtitle={`${own ? "Scans for your account" : "Scans across your organization"} · verdict trends and rules in force · UTC`}
        actions={
          <Select value={String(days)} onValueChange={(value) => setDays(Number(value) as typeof days)}>
            <SelectTrigger aria-label="Period" className="w-[160px]">
              <SelectValue />
            </SelectTrigger>
            <SelectContent align="end">
              {RANGES.map((range) => (
                <SelectItem key={range} value={String(range)}>
                  {range === 1 ? "Today" : `Last ${range} days`}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        }
      />

      {summary.error && !data ? <ErrorNotice error={summary.error} onRetry={summary.reload} /> : null}
      {!data && !summary.error ? <LoadingRows label="Loading report…" /> : null}

      {data ? (
        <>
          <div className="flex gap-4">
            <MetricCard label="Scans in period" value={data.totals.total} caption={periodCaption(data)} />
            <MetricCard
              label="Malicious / suspicious"
              value={data.totals.malicious + data.totals.suspicious}
              tone={data.totals.malicious > 0 ? "critical" : "neutral"}
              caption={`${data.totals.malicious} malicious + ${data.totals.suspicious} suspicious`}
            />
            <MetricCard label="Pending verdicts" value={data.totals.pending} caption="Waiting on VirusTotal, never clean" />
            <MetricCard label="Bypassed" value={data.totals.bypassed} tone={data.totals.bypassed > 0 ? "warning" : "neutral"} caption="A whitelist decided" />
          </div>

          <div className="flex items-start gap-5">
            <TrendChart data={data} />
            <Panel className="w-[340px] shrink-0">
              <PanelTitle>Report methodology</PanelTitle>
              <p className="text-[12px] leading-[1.6] text-subtle">
                Counts come from the scan log: one row per scanned email, message or network event, with its worst verdict.
                {own ? " Only your own scans are counted." : " Every member of the organization is counted."}
              </p>
              <MetaList
                items={[
                  ["Date basis", "Scan time, UTC days"],
                  ["Flagged", "Malicious, suspicious or pending"],
                  ["Pending", "Never counted as clean"],
                  ["Scope", own ? "Your account" : (session?.organization?.name ?? "Organization")],
                ]}
              />
            </Panel>
          </div>

          <TablePanel title="Source breakdown" meta={plural(data.bySource.length, "source")}>
            {data.bySource.length === 0 ? (
              <p className="px-4 pb-4 text-[12px] text-subtle">No scans in this period.</p>
            ) : (
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Source</TableHead>
                    <TableHead>Scans</TableHead>
                    <TableHead>Malicious</TableHead>
                    <TableHead>Suspicious</TableHead>
                    <TableHead>Pending</TableHead>
                    <TableHead>Clean</TableHead>
                    <TableHead>Bypassed</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {[...data.bySource]
                    .sort((a, b) => b.counts.total - a.counts.total)
                    .map(({ channel, counts }) => (
                      <TableRow key={channel}>
                        <TableCell className="text-ink">{sourceLabel(channel)}</TableCell>
                        <TableCell>{counts.total}</TableCell>
                        <TableCell className={cn(counts.malicious > 0 && "text-critical")}>{counts.malicious}</TableCell>
                        <TableCell className={cn(counts.suspicious > 0 && "text-warning")}>{counts.suspicious}</TableCell>
                        <TableCell>{counts.pending}</TableCell>
                        <TableCell>{counts.clean}</TableCell>
                        <TableCell>{counts.bypassed}</TableCell>
                      </TableRow>
                    ))}
                </TableBody>
              </Table>
            )}
          </TablePanel>

          <Panel>
            <PanelTitle meta="in force now">Policy activity</PanelTitle>
            <div className="flex gap-10">
              <MetaList
                className="flex-1"
                items={[
                  ["Blacklist", plural(data.rules.blacklist, "rule")],
                  ["Whitelist", plural(data.rules.whitelist, "rule")],
                ]}
              />
              <MetaList
                className="flex-1"
                items={[
                  ["Organization", plural(data.rules.organization, "rule")],
                  ["Global", `${plural(data.rules.global, "rule")} · confirmed consumer reports`],
                  [own ? "Yours" : "Personal", plural(data.rules.personal, "rule")],
                ]}
              />
            </div>
          </Panel>
        </>
      ) : null}

      <PageFootnote>
        {API_MODE === "mock" ? "Sample records" : "Live records"} · {dateLongUtc(new Date(now))} · UTC · Obsidian
        {session?.organization ? ` / ${session.organization.name}` : ""}
      </PageFootnote>
    </>
  );
}

function periodCaption(data: ReportSummary) {
  const from = data.byDay[0]?.date;
  const to = data.byDay[data.byDay.length - 1]?.date;
  return from === to ? `${from} · UTC` : `${from} to ${to} · UTC`;
}

function TrendChart({ data }: { data: ReportSummary }) {
  const max = Math.max(...data.byDay.map((d) => flagged(d.counts)), 1);
  const total = data.byDay.reduce((sum, d) => sum + flagged(d.counts), 0);
  const labelEvery = Math.ceil(data.byDay.length / 7);

  return (
    <Panel className="flex-1">
      <PanelTitle meta={`${data.byDay.length === 1 ? "today" : `${data.byDay.length} days`} · UTC`}>Flagged scans by day</PanelTitle>
      <figure
        className="flex flex-col gap-3"
        aria-label={`Flagged scans per day: ${data.byDay.map((d) => `${d.date} ${flagged(d.counts)}`).join(", ")}`}
      >
        <figcaption className="flex gap-4 text-[11px] leading-[1.4]">
          <span className="flex-1 text-subtle">Malicious, suspicious and pending · per UTC day</span>
          <span className="text-interactive">{plural(total, "flagged scan")}</span>
        </figcaption>
        <div className="flex h-[150px] items-end gap-[10px] border-b border-line px-3">
          {data.byDay.map((day) => {
            const count = flagged(day.counts);
            return (
              <div key={day.date} className="flex h-full min-w-0 flex-1 flex-col justify-end gap-2">
                <span className="text-center text-[10px] leading-[1.4] text-subtle">{count > 0 ? count : "​"}</span>
                <div
                  className={cn("w-full rounded-[2px]", count > 0 ? "bg-interactive" : "bg-line")}
                  style={{ height: count > 0 ? Math.max(6, Math.round((count / max) * 96)) : 2 }}
                  title={`${day.date}: ${day.counts.malicious} malicious, ${day.counts.suspicious} suspicious, ${day.counts.pending} pending`}
                />
              </div>
            );
          })}
        </div>
        <div className="flex gap-[10px] px-3 text-[10px] leading-[1.4] text-subtle">
          {data.byDay.map((day, index) => (
            <span key={day.date} className="min-w-0 flex-1 text-center">
              {index % labelEvery === 0 ? day.date.slice(5) : ""}
            </span>
          ))}
        </div>
      </figure>
    </Panel>
  );
}
