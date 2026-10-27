"use client";

import { useState } from "react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { ContextNotice, MetricCard, PageFootnote, PageHeading, Panel, PanelTitle } from "@/components/obsidian/panel";
import { EmptyState, ErrorNotice, LoadingRows, TablePanel } from "@/components/obsidian/table-panel";
import { useLive, useNow } from "@/components/shell/live-provider";
import { useResource } from "@/hooks/use-resource";
import { api, API_MODE, ApiError } from "@/lib/api";
import { dateLongUtc, defang, indicatorTypeLabel, plural, relativeTime } from "@/lib/format";
import type { ConsumerReportQueueItem } from "@/lib/types";

// Reports from the B2C Android app. A report VirusTotal flags, or one that enough
// verified consumers make, is blocked globally on its own; this queue holds the
// rest. Approving blocks the indicator for every organization and app user.

const key = (r: ConsumerReportQueueItem) => `${r.indicatorType} ${r.indicatorValue}`;

function statusBadge(report: ConsumerReportQueueItem) {
  if (report.protectedDomain) return <Badge variant="neutral">Protected domain</Badge>;
  if (report.status === "PROTECTED") return <Badge variant="warning">On a protected domain</Badge>;
  return <Badge variant="info">Waiting</Badge>;
}

export function ConsumerReportsView() {
  const { session } = useLive();
  const reports = useResource(api.listConsumerReports);
  const domains = useResource(api.listProtectedDomains);
  const now = useNow();
  const [pending, setPending] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [newDomain, setNewDomain] = useState("");

  const list = reports.data ?? [];
  const forbidden = reports.error instanceof ApiError && reports.error.status === 403;

  const act = async (report: ConsumerReportQueueItem, approve: boolean) => {
    setPending(key(report));
    setActionError(null);
    try {
      if (approve) await api.approveConsumerReport(report.indicatorType, report.indicatorValue);
      else await api.dismissConsumerReport(report.indicatorType, report.indicatorValue);
      reports.mutate((current = []) => current.filter((r) => key(r) !== key(report)));
    } catch (e) {
      setActionError(e instanceof Error ? e.message : "Request failed.");
    } finally {
      setPending(null);
    }
  };

  const addDomain = async () => {
    if (!newDomain.trim()) return;
    setActionError(null);
    try {
      const updated = await api.addProtectedDomain(newDomain.trim());
      domains.mutate(() => updated);
      setNewDomain("");
    } catch (e) {
      setActionError(e instanceof Error ? e.message : "Request failed.");
    }
  };

  const removeDomain = async (domain: string) => {
    setActionError(null);
    try {
      await api.removeProtectedDomain(domain);
      domains.mutate((current = []) => current.filter((d) => d !== domain));
    } catch (e) {
      setActionError(e instanceof Error ? e.message : "Request failed.");
    }
  };

  return (
    <>
      <PageHeading
        title="Consumer Reports"
        subtitle="Reports from the Obsidian Android app that need an admin before they block for everyone"
      />

      {forbidden ? (
        <ContextNotice tone="warning" title="Consumer reports are for organization admins">
          Ask an admin of {session?.organization?.name ?? "your organization"} to review them.
        </ContextNotice>
      ) : (
        <>
          <div className="flex gap-4">
            <MetricCard
              label="Waiting for review"
              value={reports.data ? list.filter((r) => r.status === "WAITING").length : "–"}
              caption="Not flagged by VirusTotal yet"
            />
            <MetricCard
              label="On protected domains"
              value={reports.data ? list.filter((r) => r.status === "PROTECTED").length : "–"}
              caption="Never blocked without an admin"
              tone="warning"
            />
          </div>

          {actionError ? (
            <ContextNotice tone="critical" title="Not changed">
              {actionError.slice(0, 200)}
            </ContextNotice>
          ) : null}

          <TablePanel
            title="Queue"
            meta={reports.data ? plural(list.length, "indicator") : undefined}
            footer="Approving adds a global block: every organization's daemons and every app user get it. Reporters are shown as a count, never by name."
          >
            {reports.error ? <ErrorNotice error={reports.error} onRetry={reports.reload} /> : null}
            {!reports.data && !reports.error ? (
              <LoadingRows label="Loading reports…" />
            ) : reports.data && list.length === 0 ? (
              <EmptyState
                title="Nothing to review"
                description="Reports VirusTotal flags, or that 3 different people make within 7 days, are blocked automatically."
              />
            ) : reports.data ? (
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Reported</TableHead>
                    <TableHead>Status</TableHead>
                    <TableHead>Reporters</TableHead>
                    <TableHead>Last report</TableHead>
                    <TableHead className="text-right">Operations</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {list.map((report) => (
                    <TableRow key={key(report)}>
                      <TableCell className="max-w-[380px]">
                        <span className="block truncate font-mono text-[12px] text-ink" title={report.indicatorValue}>
                          {defang(report.indicatorValue)}
                        </span>
                        <span className="block truncate text-[11px] text-subtle">
                          {indicatorTypeLabel[report.indicatorType]} · {report.threatCategory.toLowerCase()}
                          {report.notes.length ? ` · “${report.notes[0]}”` : ""}
                        </span>
                      </TableCell>
                      <TableCell>{statusBadge(report)}</TableCell>
                      <TableCell>
                        {report.reporters} of {report.reportersNeeded}
                      </TableCell>
                      <TableCell>{relativeTime(report.lastReportedAt, now)}</TableCell>
                      <TableCell className="text-right">
                        <div className="flex justify-end gap-2">
                          <Button
                            size="sm"
                            disabled={pending === key(report) || report.protectedDomain}
                            title={report.protectedDomain ? "Protected domains can't be blocked for everyone. Block it in Rules and Policies for your organization." : undefined}
                            onClick={() => act(report, true)}
                          >
                            Block for everyone
                          </Button>
                          <Button size="sm" variant="outline" disabled={pending === key(report)} onClick={() => act(report, false)}>
                            Dismiss
                          </Button>
                        </div>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            ) : null}
          </TablePanel>

          <Panel>
            <PanelTitle meta={domains.data ? plural(domains.data.length, "domain") : undefined}>Protected domains</PanelTitle>
            <p className="text-[12px] leading-[1.6] text-subtle">
              The top 1,000 sites are always protected. Add your own domains so a consumer report can never block them for
              everyone. Subdomains are protected too.
            </p>
            <form
              className="mt-3 flex max-w-[480px] gap-2.5"
              onSubmit={(e) => {
                e.preventDefault();
                void addDomain();
              }}
            >
              <Input
                value={newDomain}
                onChange={(e) => setNewDomain(e.target.value)}
                placeholder="aster.example"
                aria-label="Domain to protect"
              />
              <Button type="submit" variant="outline" disabled={!newDomain.trim()}>
                Protect
              </Button>
            </form>
            {domains.error ? <ErrorNotice error={domains.error} onRetry={domains.reload} /> : null}
            <ul className="mt-3 flex flex-wrap gap-2">
              {(domains.data ?? []).map((domain) => (
                <li key={domain} className="flex items-center gap-1.5 rounded-sm bg-canvas px-2 py-1 font-mono text-[12px] text-ink">
                  {domain}
                  <button
                    type="button"
                    className="text-subtle hover:text-critical"
                    aria-label={`Stop protecting ${domain}`}
                    onClick={() => void removeDomain(domain)}
                  >
                    ×
                  </button>
                </li>
              ))}
            </ul>
          </Panel>
        </>
      )}

      <PageFootnote>
        {API_MODE === "mock" ? "Sample records" : "Live records"} · {dateLongUtc(new Date(now))} · UTC · Obsidian
        {session?.organization ? ` / ${session.organization.name}` : ""}
      </PageFootnote>
    </>
  );
}
