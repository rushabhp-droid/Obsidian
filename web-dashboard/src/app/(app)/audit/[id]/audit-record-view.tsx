"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useCallback } from "react";
import { Badge } from "@/components/ui/badge";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { ContextNotice, MetaList, PageFootnote, PageHeading, Panel, PanelTitle } from "@/components/obsidian/panel";
import { ErrorNotice, LoadingRows, TablePanel } from "@/components/obsidian/table-panel";
import { useLive, useNow } from "@/components/shell/live-provider";
import { useResource } from "@/hooks/use-resource";
import { api, API_MODE } from "@/lib/api";
import { dateLongUtc, indicatorTypeLabel, plural, ruleSourceLabel, shortId, timeUtc } from "@/lib/format";
import { actionLabel, actorOf, changeOf, recordId, resourceOf, scopeOf } from "../audit-format";

/** One read-only audit record, with the rule it changed as it is now and that rule's full history. */
export function AuditRecordView() {
  const { id } = useParams<{ id: string }>();
  const load = useCallback(() => api.auditDetail(id), [id]);
  const detail = useResource(load);
  const { session } = useLive();
  const now = useNow();

  if (detail.error && !detail.data) {
    return (
      <>
        <PageHeading title="Audit record detail" />
        <ErrorNotice error={detail.error} onRetry={detail.reload} />
        <BackLink />
      </>
    );
  }
  if (!detail.data) return <LoadingRows label="Loading audit record…" />;

  const { entry, rule, ruleHistory } = detail.data;
  const details = Object.entries(entry.details).filter(([, value]) => value !== null && value !== undefined);

  return (
    <>
      <PageHeading
        title="Audit record detail"
        subtitle={`${recordId(entry)} · ${dateLongUtc(new Date(entry.createdAt))}, ${timeUtc(entry.createdAt)} · Read-only`}
      />

      <ContextNotice title="Read-only audit record">
        No edit or delete action exists. A record states what was requested and stored, not that every host has applied it.
      </ContextNotice>

      <div className="flex items-start gap-5">
        <Panel className="flex-[2]">
          <PanelTitle>Record properties</PanelTitle>
          <MetaList
            items={[
              ["Record", <span key="r" className="font-mono text-[11px]">{entry.id}</span>],
              ["Timestamp", entry.createdAt],
              ["Actor", entry.actor ? `${entry.actor.displayName} · ${entry.actor.email}` : actorOf(entry)],
              ["Action", actionLabel[entry.action]],
              ["Resource", resourceOf(entry)],
              ["Change", changeOf(entry)],
              ["Scope", scopeOf(entry) === "Global" ? "Global · every organization and app user" : scopeOf(entry)],
            ]}
          />
        </Panel>
        <Panel className="flex-1">
          <PanelTitle>Matching rule</PanelTitle>
          {rule ? (
            <>
              <Badge variant={rule.listType === "BLACKLIST" ? "critical" : "verified"}>
                {rule.listType === "BLACKLIST" ? "Blocking now" : "Allowed now"}
              </Badge>
              <p className="font-mono text-[12px] break-all text-ink">{rule.indicatorValue}</p>
              <p className="text-[12px] leading-[1.6] text-subtle">
                {indicatorTypeLabel[rule.indicatorType]} · {ruleSourceLabel[rule.source]} ·{" "}
                {rule.userId ? "personal rule" : rule.orgId ? "organization rule" : "global rule"}
                {rule.expiresAt ? ` · expires ${dateLongUtc(new Date(rule.expiresAt))} ${timeUtc(rule.expiresAt)}` : ""}
              </p>
              <Link href={`/firewall?q=${encodeURIComponent(rule.indicatorValue)}`} className="w-fit text-[12px] text-interactive hover:underline">
                Open in Rules and Policies →
              </Link>
            </>
          ) : (
            <p className="text-[12px] leading-[1.6] text-subtle">
              {entry.ruleId ? "The rule no longer exists." : "This record isn't about a rule."}
            </p>
          )}
        </Panel>
      </div>

      {details.length > 0 ? (
        <TablePanel title="Stored details" meta={`${details.length} fields`}>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Field</TableHead>
                <TableHead>Value</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {details.map(([field, value]) => (
                <TableRow key={field}>
                  <TableCell className="text-ink">{field}</TableCell>
                  <TableCell className="font-mono text-[11px] break-all">
                    {typeof value === "object" ? JSON.stringify(value) : String(value)}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TablePanel>
      ) : null}

      {ruleHistory.length > 0 ? (
        <TablePanel title="Rule history" meta={`${plural(ruleHistory.length, "record")} · oldest first`}>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Record / time</TableHead>
                <TableHead>Actor</TableHead>
                <TableHead>Action</TableHead>
                <TableHead>Previous → new</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {ruleHistory.map((h) => (
                <TableRow key={h.id} data-state={h.id === entry.id ? "selected" : undefined}>
                  <TableCell>
                    <Link href={`/audit/${h.id}`} className="block font-mono text-[11px] text-interactive hover:underline">
                      {recordId(h)}
                    </Link>
                    <span className="block text-[11px]">
                      {h.createdAt.slice(0, 10)} · {timeUtc(h.createdAt)}
                    </span>
                  </TableCell>
                  <TableCell className="text-ink">{actorOf(h)}</TableCell>
                  <TableCell>{actionLabel[h.action]}</TableCell>
                  <TableCell>{changeOf(h)}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TablePanel>
      ) : null}

      <BackLink />

      <PageFootnote>
        {API_MODE === "mock" ? "Sample records" : "Live records"} · {dateLongUtc(new Date(now))} · UTC · Obsidian
        {session?.organization ? ` / ${session.organization.name}` : ""} · {shortId("AUD", entry.id)}
      </PageFootnote>
    </>
  );
}

function BackLink() {
  return (
    <Link href="/audit" className="w-fit text-[12px] leading-[1.4] text-interactive hover:underline">
      ← Audit log
    </Link>
  );
}
