"use client";

import { useEffect, useMemo, useState } from "react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { ContextNotice, MetricCard, PageFootnote, PageHeading } from "@/components/obsidian/panel";
import { EmptyState, ErrorNotice, LoadingRows, TablePanel } from "@/components/obsidian/table-panel";
import { useLive, useNow } from "@/components/shell/live-provider";
import { useResource } from "@/hooks/use-resource";
import { api, API_MODE, ApiError } from "@/lib/api";
import { dateLongUtc, plural, relativeTime } from "@/lib/format";
import type { CodeFinding, CodeFindingStatus, CodeFindingType, CodeSeverity } from "@/lib/types";

// A leaked key is compromised once pushed: the page drives
// rotation and never shows the key. Only its SHA-256 fingerprint exists on the server.

const typeLabel: Record<CodeFindingType, string> = {
  SECRET: "Leaked secret",
  VULN_DEPENDENCY: "Vulnerable dependency",
  MALICIOUS_BINARY: "Malicious release binary",
  VULN_BINARY: "Vulnerable release binary",
};
const statusLabel: Record<CodeFindingStatus, string> = { OPEN: "Open", REVOKED: "Rotated", FALSE_POSITIVE: "False positive" };
const severityVariant: Record<CodeSeverity, "critical" | "warning" | "neutral"> = {
  CRITICAL: "critical",
  HIGH: "critical",
  MEDIUM: "warning",
  LOW: "neutral",
};
const severityOrder: CodeSeverity[] = ["CRITICAL", "HIGH", "MEDIUM", "LOW"];

const shortRef = (finding: CodeFinding) => (finding.trigger === "RELEASE" ? finding.gitRef : finding.gitRef.slice(0, 7));
const triggerLabel = { PUSH: "Push", PR: "Pull request", RELEASE: "Release" } as const;

function guidance(finding: CodeFinding) {
  switch (finding.findingType) {
    case "SECRET":
      return "Rotate this key at its provider, then mark it rotated. Deleting it from the code doesn't make it safe again.";
    case "VULN_DEPENDENCY":
      return "Upgrade the package to a fixed version.";
    default:
      return "Pull the release asset and rebuild it from a clean environment.";
  }
}

export function CodeView() {
  const { session, codeVersion } = useLive();
  const findings = useResource(api.listCodeFindings);
  const now = useNow();
  const [repo, setRepo] = useState("all");
  const [type, setType] = useState<"all" | CodeFindingType>("all");
  const [status, setStatus] = useState<"all" | CodeFindingStatus>("OPEN");
  const [pending, setPending] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const { reload, mutate } = findings;

  useEffect(() => {
    if (codeVersion > 0) reload();
  }, [codeVersion, reload]);

  const list = useMemo(() => findings.data ?? [], [findings.data]);
  const repos = [...new Set(list.map((f) => f.repoFullName))].sort();
  const open = list.filter((f) => f.status === "OPEN");
  const filtered = list
    .filter((f) => repo === "all" || f.repoFullName === repo)
    .filter((f) => type === "all" || f.findingType === type)
    .filter((f) => status === "all" || f.status === status)
    .sort((a, b) => severityOrder.indexOf(a.severity) - severityOrder.indexOf(b.severity) || b.createdAt.localeCompare(a.createdAt));
  const forbidden = findings.error instanceof ApiError && findings.error.status === 403;

  const decide = async (finding: CodeFinding, next: "REVOKED" | "FALSE_POSITIVE") => {
    setPending(finding.id);
    setActionError(null);
    try {
      const updated = await api.setCodeFindingStatus(finding.id, next);
      mutate((current = []) => current.map((f) => (f.id === updated.id ? updated : f)));
    } catch (e) {
      setActionError(e instanceof Error ? e.message : "Request failed.");
    } finally {
      setPending(null);
    }
  };

  return (
    <>
      <PageHeading
        title="Code Guard"
        subtitle="GitHub pushes, pull requests and releases · leaked secrets, vulnerable dependencies, malicious binaries"
      />

      {forbidden ? (
        <ContextNotice tone="warning" title="Code Guard findings are for organization admins">
          Ask an admin of {session?.organization?.name ?? "your organization"} to review them.
        </ContextNotice>
      ) : (
        <>
          <div className="flex gap-4">
            <MetricCard
              label="Open critical / high"
              value={findings.data ? open.filter((f) => f.severity === "CRITICAL" || f.severity === "HIGH").length : "–"}
              caption="Fail the Check Run on GitHub"
              tone="critical"
            />
            <MetricCard
              label="Leaked secrets to rotate"
              value={findings.data ? open.filter((f) => f.findingType === "SECRET").length : "–"}
              caption="Compromised once pushed"
            />
            <MetricCard
              label="Vulnerable dependencies"
              value={findings.data ? open.filter((f) => f.findingType === "VULN_DEPENDENCY").length : "–"}
              caption="OSV-Scanner on every push and pull request"
            />
            <MetricCard
              label="Malicious release binaries"
              value={findings.data ? open.filter((f) => f.findingType === "MALICIOUS_BINARY").length : "–"}
              caption="Release assets checked with VirusTotal"
            />
          </div>

          {actionError ? (
            <ContextNotice tone="critical" title="Not changed">
              {actionError.slice(0, 200)}
            </ContextNotice>
          ) : null}

          <TablePanel
            title="Findings"
            meta={findings.data ? plural(filtered.length, "finding") : undefined}
            toolbar={
              <div className="flex flex-wrap gap-2.5 pb-2">
                <Select value={repo} onValueChange={setRepo}>
                  <SelectTrigger className="w-[260px]" aria-label="Repository">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="all">All repositories</SelectItem>
                    {repos.map((r) => (
                      <SelectItem key={r} value={r}>
                        {r}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                <Select value={type} onValueChange={(v) => setType(v as typeof type)}>
                  <SelectTrigger className="w-[220px]" aria-label="Finding type">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="all">All finding types</SelectItem>
                    {(Object.keys(typeLabel) as CodeFindingType[]).map((t) => (
                      <SelectItem key={t} value={t}>
                        {typeLabel[t]}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                <Select value={status} onValueChange={(v) => setStatus(v as typeof status)}>
                  <SelectTrigger className="w-[170px]" aria-label="Status">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="all">Any status</SelectItem>
                    {(Object.keys(statusLabel) as CodeFindingStatus[]).map((s) => (
                      <SelectItem key={s} value={s}>
                        {statusLabel[s]}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            }
            footer="Secrets are stored as a SHA-256 fingerprint only, never their value."
          >
            {findings.error ? <ErrorNotice error={findings.error} onRetry={reload} /> : null}
            {!findings.data && !findings.error ? (
              <LoadingRows label="Loading findings…" />
            ) : findings.data && filtered.length === 0 ? (
              <EmptyState
                title={list.length === 0 ? "No findings yet" : "No findings in this view"}
                description={
                  list.length === 0
                    ? "Install the Obsidian GitHub App on a repository; pushes, pull requests and releases are scanned from then on."
                    : "Change the filters to see more."
                }
                onClear={list.length === 0 ? undefined : () => (setRepo("all"), setType("all"), setStatus("all"))}
              />
            ) : findings.data ? (
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Severity</TableHead>
                    <TableHead>Finding</TableHead>
                    <TableHead>Where</TableHead>
                    <TableHead>Detected</TableHead>
                    <TableHead>Status</TableHead>
                    <TableHead className="text-right">Operations</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {filtered.map((finding) => (
                    <TableRow key={finding.id}>
                      <TableCell>
                        <Badge variant={severityVariant[finding.severity]}>{finding.severity}</Badge>
                      </TableCell>
                      <TableCell className="max-w-[320px]">
                        <span className="block text-ink">{typeLabel[finding.findingType]}</span>
                        <span className="block truncate font-mono text-[11px] text-subtle" title={guidance(finding)}>
                          {finding.ruleId}
                          {finding.secretFingerprint ? ` · sha256 ${finding.secretFingerprint.slice(0, 12)}…` : ""}
                        </span>
                      </TableCell>
                      <TableCell className="max-w-[300px]">
                        <span className="block truncate text-ink">{finding.repoFullName}</span>
                        <span className="block truncate font-mono text-[11px] text-subtle">
                          {finding.location ?? "–"} · {triggerLabel[finding.trigger]} {shortRef(finding)}
                        </span>
                      </TableCell>
                      <TableCell>{relativeTime(finding.createdAt, now)}</TableCell>
                      <TableCell>{statusLabel[finding.status]}</TableCell>
                      <TableCell className="text-right">
                        {finding.status === "OPEN" ? (
                          <div className="flex justify-end gap-2">
                            {finding.findingType === "SECRET" ? (
                              <Button size="sm" disabled={pending === finding.id} onClick={() => decide(finding, "REVOKED")}>
                                Mark rotated
                              </Button>
                            ) : null}
                            <Button
                              size="sm"
                              variant="outline"
                              disabled={pending === finding.id}
                              onClick={() => decide(finding, "FALSE_POSITIVE")}
                            >
                              False positive
                            </Button>
                          </div>
                        ) : null}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            ) : null}
          </TablePanel>
        </>
      )}

      <PageFootnote>
        {API_MODE === "mock" ? "Sample records" : "Live records"} · {dateLongUtc(new Date(now))} · UTC · Obsidian
        {session?.organization ? ` / ${session.organization.name}` : ""}
      </PageFootnote>
    </>
  );
}
