"use client";

import Image from "next/image";
import { useSearchParams } from "next/navigation";
import { useEffect, useMemo, useRef, useState } from "react";
import { Button } from "@/components/ui/button";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { ContextNotice, MetaList, PageFootnote, PageHeading, Panel, PanelTitle } from "@/components/obsidian/panel";
import { ConfirmRuleChange, NewRuleDialog, type RuleChange } from "@/components/obsidian/rule-dialogs";
import { EmptyState, ErrorNotice, LoadingRows } from "@/components/obsidian/table-panel";
import { useLive, useNow } from "@/components/shell/live-provider";
import { useLiveRules } from "@/hooks/use-live-data";
import { API_MODE } from "@/lib/api";
import { dateLongUtc, indicatorTypeLabel, isOnline, plural, relativeTime, ruleSourceLabel, timeUtc } from "@/lib/format";
import { isNetworkIndicator } from "@/lib/indicators";
import type { IndicatorType, ThreatRule } from "@/lib/types";
import { cn } from "@/lib/utils";

type Scope = "all" | "org" | "user" | "global";
const PAGE_SIZE = 10;

const scopeOf = (rule: ThreatRule): Exclude<Scope, "all"> =>
  rule.userId ? "user" : rule.orgId ? "org" : "global";
const scopeLabel = { org: "Organization", user: "User", global: "Global" } as const;
const titleCase = (value: string) => value.charAt(0) + value.slice(1).toLowerCase();
const dayTime = (iso: string) =>
  `${new Date(iso).toLocaleDateString("en-GB", { day: "numeric", month: "short", timeZone: "UTC" })} · ${timeUtc(iso)}`;

export function RulesView() {
  const params = useSearchParams();
  const rules = useLiveRules();
  const { session, agents, agentsError, events } = useLive();
  const now = useNow();
  const searchRef = useRef<HTMLInputElement>(null);

  const [scope, setScope] = useState<Scope>("all");
  const [query, setQuery] = useState(params.get("q") ?? "");
  const [type, setType] = useState<IndicatorType | "all">("all");
  const [list, setList] = useState<"all" | "BLACKLIST" | "WHITELIST">("all");
  const [expiry, setExpiry] = useState<"all" | "expiring" | "permanent">("all");
  const [page, setPage] = useState(0);
  const [creating, setCreating] = useState(false);
  const [change, setChange] = useState<RuleChange | null>(null);

  useEffect(() => setQuery(params.get("q") ?? ""), [params]);

  // ⌘K / Ctrl+K focuses the search field, as the hint in the field says.
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
    return (rules.data ?? [])
      .filter((r) => scope === "all" || scopeOf(r) === scope)
      .filter((r) => type === "all" || r.indicatorType === type)
      .filter((r) => list === "all" || r.listType === list)
      .filter((r) => expiry === "all" || (expiry === "expiring" ? r.expiresAt !== null : r.expiresAt === null))
      .filter(
        (r) =>
          !q ||
          r.indicatorValue.includes(q) ||
          r.threatCategory.toLowerCase().includes(q) ||
          ruleSourceLabel[r.source].toLowerCase().includes(q),
      )
      .sort((a, b) => b.updatedAt.localeCompare(a.updatedAt));
  }, [rules.data, scope, type, list, expiry, query]);

  useEffect(() => setPage(0), [scope, type, list, expiry, query]);

  const pageCount = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE));
  const rows = filtered.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE);
  const onlineAgents = (agents ?? []).filter((a) => isOnline(a.lastHeartbeatAt, now));
  const networkBlocks = (rules.data ?? []).filter((r) => r.listType === "BLACKLIST" && isNetworkIndicator(r.indicatorType));
  const lastRuleEvent = events.find((e) => e.type !== "SCAN");
  const filtersActive = query !== "" || type !== "all" || list !== "all" || expiry !== "all";
  const clearFilters = () => {
    setQuery("");
    setType("all");
    setList("all");
    setExpiry("all");
  };

  const counts = useMemo(() => {
    const all = rules.data ?? [];
    return {
      org: all.filter((r) => scopeOf(r) === "org").length,
      user: all.filter((r) => scopeOf(r) === "user").length,
      global: all.filter((r) => scopeOf(r) === "global").length,
    };
  }, [rules.data]);

  return (
    <>
      <PageHeading
        title="Rules and policies"
        subtitle="One blacklist and whitelist for email, messaging and the network · Changes reach connected agents in real time"
        actions={<Button onClick={() => setCreating(true)}>New rule</Button>}
      />

      <Tabs value={scope} onValueChange={(v) => setScope(v as Scope)}>
        <TabsList aria-label="Rule scope">
          <TabsTrigger value="all">All scopes</TabsTrigger>
          <TabsTrigger value="org">Organization · {counts.org}</TabsTrigger>
          <TabsTrigger value="user">User · {counts.user}</TabsTrigger>
          <TabsTrigger value="global">Global · {counts.global}</TabsTrigger>
        </TabsList>
      </Tabs>

      <div className="flex items-center gap-2.5">
        <label className="flex h-9 min-w-0 flex-1 items-center gap-[9px] rounded-md border border-line bg-surface px-3 focus-within:border-interactive focus-within:ring-1 focus-within:ring-interactive">
          <Image src="/icons/search.svg" alt="" width={16} height={16} unoptimized />
          <span className="sr-only">Search rules</span>
          <input
            ref={searchRef}
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="Search indicator, category or source…"
            className="min-w-0 flex-1 bg-transparent text-[12px] leading-[1.4] text-ink outline-none placeholder:text-subtle focus-visible:outline-none"
          />
          <kbd className="font-sans text-[11px] text-subtle">⌘ K</kbd>
        </label>
        <Select value={type} onValueChange={(v) => setType(v as IndicatorType | "all")}>
          <SelectTrigger aria-label="Indicator type">
            <SelectValue />
          </SelectTrigger>
          <SelectContent align="end">
            <SelectItem value="all">Type</SelectItem>
            {(Object.keys(indicatorTypeLabel) as IndicatorType[]).map((t) => (
              <SelectItem key={t} value={t}>
                {indicatorTypeLabel[t]}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Select value={list} onValueChange={(v) => setList(v as typeof list)}>
          <SelectTrigger aria-label="List">
            <SelectValue />
          </SelectTrigger>
          <SelectContent align="end">
            <SelectItem value="all">List</SelectItem>
            <SelectItem value="BLACKLIST">Blacklist</SelectItem>
            <SelectItem value="WHITELIST">Whitelist</SelectItem>
          </SelectContent>
        </Select>
        <Select value={expiry} onValueChange={(v) => setExpiry(v as typeof expiry)}>
          <SelectTrigger aria-label="Expiry">
            <SelectValue />
          </SelectTrigger>
          <SelectContent align="end">
            <SelectItem value="all">Expiry</SelectItem>
            <SelectItem value="expiring">Has expiry</SelectItem>
            <SelectItem value="permanent">No expiry</SelectItem>
          </SelectContent>
        </Select>
      </div>

      <section className="flex min-w-0 flex-col rounded-lg border border-line bg-surface" aria-label="Rules">
        {rules.error ? <ErrorNotice error={rules.error} onRetry={rules.reload} /> : null}
        {rules.data === undefined && !rules.error ? (
          <LoadingRows label="Loading rules…" />
        ) : rows.length === 0 && rules.data ? (
          <EmptyState
            title="No rules in this view"
            description={
              filtersActive
                ? `No matches${query ? ` for “${query}”` : ""}. Broaden the query or clear filters; this does not mean the indicator is safe.`
                : "Nothing is listed for this scope yet. Report an indicator to add the first rule."
            }
            onClear={filtersActive ? clearFilters : undefined}
          />
        ) : rows.length > 0 ? (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Indicator</TableHead>
                <TableHead>Scope / source</TableHead>
                <TableHead>List / category</TableHead>
                <TableHead>Expiry / TTL</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Updated</TableHead>
                <TableHead className="text-right">Action</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {rows.map((rule) => {
                const expired = rule.expiresAt !== null && new Date(rule.expiresAt).getTime() <= now;
                const status = ruleStatus(rule, expired, onlineAgents.length);
                return (
                  <TableRow key={rule.id}>
                    <TableCell className="max-w-[300px]">
                      <span className="block font-mono text-[11px] break-all text-ink">{rule.indicatorValue}</span>
                      <span className="block">{indicatorTypeLabel[rule.indicatorType]}</span>
                    </TableCell>
                    <TableCell>
                      <span className="block">{scopeLabel[scopeOf(rule)]}</span>
                      <span className="block">{ruleSourceLabel[rule.source]}</span>
                    </TableCell>
                    <TableCell>
                      <span className="block text-ink">
                        {rule.listType === "BLACKLIST" ? "Blacklist" : "Whitelist"}
                      </span>
                      <span className="block">{titleCase(rule.threatCategory)}</span>
                    </TableCell>
                    <TableCell>
                      {rule.expiresAt ? (
                        <>
                          <span className="block">{dayTime(rule.expiresAt)}</span>
                          <span className={cn("block", expired && "text-warning")}>
                            {expired ? "Expired" : `Expires ${relativeTime(rule.expiresAt, now)}`}
                          </span>
                        </>
                      ) : (
                        "No expiry"
                      )}
                    </TableCell>
                    <TableCell>
                      <span className={cn("block", status.tone)}>{status.label}</span>
                      <span className="block">{status.detail}</span>
                    </TableCell>
                    <TableCell>
                      <span className="block">{dayTime(rule.updatedAt)}</span>
                      <span className="block">{relativeTime(rule.updatedAt, now)}</span>
                    </TableCell>
                    <TableCell className="text-right">
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() =>
                          setChange({
                            action: rule.listType === "BLACKLIST" ? "bypass" : "report",
                            indicatorType: rule.indicatorType,
                            indicatorValue: rule.indicatorValue,
                            currentList: rule.listType,
                          })
                        }
                      >
                        {rule.listType === "BLACKLIST" ? "Bypass" : "Report"}
                      </Button>
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        ) : null}
        {rules.data ? (
          <div className="flex items-center gap-5 px-4 py-[13px] text-[11px] text-subtle">
            <span>
              Showing {rows.length} of {plural(filtered.length, "record")}
            </span>
            <Button variant="link" className="text-[11px]" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
              Previous
            </Button>
            <span className="text-interactive">{page + 1}</span>
            <Button
              variant="link"
              className="text-[11px]"
              disabled={page >= pageCount - 1}
              onClick={() => setPage((p) => p + 1)}
            >
              Next
            </Button>
          </div>
        ) : null}
      </section>

      <ContextNotice title="Network enforcement covers domains and IP addresses">
        Agents sinkhole blacklisted domains in /etc/hosts and drop blacklisted IPs in the OBSIDIAN iptables chain. URLs, senders,
        phone numbers and file hashes apply to email and message scans. /etc/hosts doesn&apos;t cover subdomains, and browsers using
        DNS-over-HTTPS skip it.
      </ContextNotice>

      <div className="flex items-start gap-5">
        <Panel className="flex-1">
          <PanelTitle>Evaluation hierarchy</PanelTitle>
          <p className="text-[12px] leading-[1.4] text-ink">
            Organization admin policy → unexpired user whitelist, only if the organization permits bypass → user blacklist →
            organization blacklist → global rules → VirusTotal and heuristics.
          </p>
          <p className="text-[12px] leading-[1.4] text-subtle">
            The first match wins. Expired entries do not apply. Report and Bypass change an indicator&apos;s list; they never
            create a second rule for it.
            {session?.organization && !session.organization.policyAllowUserBypass
              ? ` User bypass is turned off for ${session.organization.name}.`
              : ""}
          </p>
        </Panel>
        <Panel className="flex-1">
          <PanelTitle>Current enforcement</PanelTitle>
          <MetaList
            items={[
              [
                "Agents online",
                agents
                  ? `${onlineAgents.length} of ${agents.length}${onlineAgents[0] ? ` · ${onlineAgents[0].hostname}` : ""}`
                  : agentsError
                    ? "Unavailable"
                    : "Loading…",
              ],
              ["Network blocks", `${plural(networkBlocks.length, "domain or IP", "domains and IPs")} pushed to agents`],
              [
                "Last change",
                lastRuleEvent
                  ?`${lastRuleEvent.type === "BLOCK" ? "Blocked" : "Bypassed"} ${lastRuleEvent.value} · ${timeUtc(lastRuleEvent.at)}`
                  : "None since this page opened",
              ],
              ["Enforcement", onlineAgents.length > 0 ? "iptables OBSIDIAN chain + /etc/hosts" : "Unavailable · no agent online"],
            ]}
          />
        </Panel>
      </div>

      <PageFootnote>
        {API_MODE === "mock" ? "Sample records" : "Live records"} · {dateLongUtc(new Date(now))} · UTC · Obsidian
        {session?.organization ? ` / ${session.organization.name}` : ""}
      </PageFootnote>

      <NewRuleDialog
        open={creating}
        onOpenChange={setCreating}
        onSubmit={(next) => {
          setCreating(false);
          setChange(next);
        }}
      />
      {change ? (
        <ConfirmRuleChange change={change} onOpenChange={(open) => !open && setChange(null)} onDone={() => rules.reload()} />
      ) : null}
    </>
  );
}

function ruleStatus(rule: ThreatRule, expired: boolean, agentsOnline: number) {
  if (expired) return { label: "Expired", detail: "No longer applies", tone: "text-subtle" };
  const network = isNetworkIndicator(rule.indicatorType);
  if (rule.listType === "WHITELIST") {
    return { label: "Allowed", detail: network ? "Block lifted on agents" : "Scans skip this indicator", tone: "text-ink" };
  }
  if (!network) return { label: "Blocking", detail: "Email and message scans", tone: "text-ink" };
  return agentsOnline > 0
    ? { label: "Blocking", detail: `On ${plural(agentsOnline, "agent")}`, tone: "text-ink" }
    : { label: "Not enforced", detail: "No agent online", tone: "text-warning" };
}
