"use client";

import Image from "next/image";
import Link from "next/link";
import { useMemo, useState } from "react";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { ContextNotice, PageFootnote, PageHeading } from "@/components/obsidian/panel";
import { EmptyState, ErrorNotice, LoadingRows, TablePanel } from "@/components/obsidian/table-panel";
import { useLive, useNow } from "@/components/shell/live-provider";
import { useResource } from "@/hooks/use-resource";
import { api, API_MODE } from "@/lib/api";
import { dateLongUtc, plural, timeUtc } from "@/lib/format";
import type { AuditEntry } from "@/lib/types";
import {
  actionLabel,
  actorOf,
  categoryLabel,
  categoryOf,
  changeOf,
  recordId,
  resourceOf,
  scopeOf,
  type AuditCategory,
} from "./audit-format";

const PAGE_SIZE = 15;
const loadAudit = () => api.listAudit(500);

/** Append-only record of rule, membership and settings changes. No edit or delete controls exist. */
export function AuditView() {
  const audit = useResource(loadAudit);
  const { session } = useLive();
  const now = useNow();
  const [tab, setTab] = useState<"all" | AuditCategory>("all");
  const [query, setQuery] = useState("");
  const [actor, setActor] = useState("all");
  const [page, setPage] = useState(0);

  const list = useMemo(() => audit.data ?? [], [audit.data]);
  const actors = [...new Set(list.map(actorOf))].sort();
  const filtered = list
    .filter((e) => tab === "all" || categoryOf[e.action] === tab)
    .filter((e) => actor === "all" || actorOf(e) === actor)
    .filter((e) => {
      const q = query.trim().toLowerCase();
      return !q || [recordId(e), actionLabel[e.action], resourceOf(e), actorOf(e)].some((v) => v.toLowerCase().includes(q));
    });
  const pageCount = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE));
  const current = Math.min(page, pageCount - 1);
  const rows = filtered.slice(current * PAGE_SIZE, (current + 1) * PAGE_SIZE);
  const categories = (Object.keys(categoryLabel) as AuditCategory[]).filter((c) => list.some((e) => categoryOf[e.action] === c));

  return (
    <>
      <PageHeading
        title="Audit log"
        subtitle={`Append-only · ${dateLongUtc(new Date(now))} · UTC · No edit or delete controls`}
      />

      <ContextNotice title="Recorded by the server">
        Every report, bypass, consumer-report promotion, membership and settings change is written in the same transaction
        as the change itself. Records here are read-only.
      </ContextNotice>

      <div className="flex items-center gap-2.5">
        <label className="flex h-9 min-w-0 flex-1 items-center gap-[9px] rounded-md border border-line bg-surface px-3 focus-within:border-interactive focus-within:ring-1 focus-within:ring-interactive">
          <Image src="/icons/search.svg" alt="" width={16} height={16} unoptimized />
          <span className="sr-only">Search audit records</span>
          <input
            value={query}
            onChange={(event) => {
              setQuery(event.target.value);
              setPage(0);
            }}
            placeholder="Search action, resource or record ID…"
            className="min-w-0 flex-1 bg-transparent text-[12px] leading-[1.4] text-ink outline-none placeholder:text-subtle"
          />
        </label>
        <Select
          value={actor}
          onValueChange={(value) => {
            setActor(value);
            setPage(0);
          }}
        >
          <SelectTrigger aria-label="Actor">
            <SelectValue />
          </SelectTrigger>
          <SelectContent align="end">
            <SelectItem value="all">Actor</SelectItem>
            {actors.map((name) => (
              <SelectItem key={name} value={name}>
                {name}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      <Tabs
        value={tab}
        onValueChange={(value) => {
          setTab(value as typeof tab);
          setPage(0);
        }}
      >
        <TabsList aria-label="Record type">
          <TabsTrigger value="all">All records</TabsTrigger>
          {categories.map((c) => (
            <TabsTrigger key={c} value={c}>
              {categoryLabel[c]}
            </TabsTrigger>
          ))}
        </TabsList>
      </Tabs>

      <TablePanel
        title="Records"
        meta={audit.data ? plural(filtered.length, "record") : undefined}
        footer={
          audit.data && filtered.length > 0 ? (
            <span className="flex items-center gap-5">
              {`Showing ${rows.length} of ${plural(filtered.length, "record")} · newest first`}
              <button type="button" className="text-interactive disabled:text-subtle" disabled={current === 0} onClick={() => setPage(current - 1)}>
                Previous
              </button>
              <span className="text-interactive">{current + 1}</span>
              <button
                type="button"
                className="text-interactive disabled:text-subtle"
                disabled={current >= pageCount - 1}
                onClick={() => setPage(current + 1)}
              >
                Next
              </button>
            </span>
          ) : undefined
        }
      >
        {audit.error ? <ErrorNotice error={audit.error} onRetry={audit.reload} /> : null}
        {!audit.data && !audit.error ? (
          <LoadingRows label="Loading audit records…" />
        ) : audit.data && filtered.length === 0 ? (
          <EmptyState
            title={list.length === 0 ? "No records yet" : "No records in this view"}
            description={list.length === 0 ? "Reports, bypasses and settings changes appear here as they happen." : "Change the filters to see more."}
          />
        ) : audit.data ? (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Record / time</TableHead>
                <TableHead>Actor</TableHead>
                <TableHead>Action</TableHead>
                <TableHead>Resource</TableHead>
                <TableHead>Previous → new</TableHead>
                <TableHead>Scope</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {rows.map((entry) => (
                <AuditRow key={entry.id} entry={entry} />
              ))}
            </TableBody>
          </Table>
        ) : null}
      </TablePanel>

      <PageFootnote>
        {API_MODE === "mock" ? "Sample records" : "Live records"} · {dateLongUtc(new Date(now))} · UTC · Obsidian
        {session?.organization ? ` / ${session.organization.name}` : ""}
      </PageFootnote>
    </>
  );
}

function AuditRow({ entry }: { entry: AuditEntry }) {
  return (
    <TableRow>
      <TableCell>
        <Link href={`/audit/${entry.id}`} className="block font-mono text-[11px] text-interactive hover:underline">
          {recordId(entry)}
        </Link>
        <span className="block text-[11px]">
          {entry.createdAt.slice(0, 10)} · {timeUtc(entry.createdAt)}
        </span>
      </TableCell>
      <TableCell className="text-ink">{actorOf(entry)}</TableCell>
      <TableCell>{actionLabel[entry.action]}</TableCell>
      <TableCell className="max-w-[240px] truncate font-mono text-[11px]" title={resourceOf(entry)}>
        {resourceOf(entry)}
      </TableCell>
      <TableCell className="max-w-[240px]">{changeOf(entry)}</TableCell>
      <TableCell>{scopeOf(entry)}</TableCell>
    </TableRow>
  );
}
