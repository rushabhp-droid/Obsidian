"use client";

import Image from "next/image";
import Link from "next/link";
import { useMemo, useState } from "react";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { ContextNotice, MetaList, MetricCard, PageFootnote, PageHeading, Panel, PanelTitle } from "@/components/obsidian/panel";
import { EmptyState, ErrorNotice, LoadingRows, TablePanel } from "@/components/obsidian/table-panel";
import { useLive, useNow } from "@/components/shell/live-provider";
import { useLiveRules } from "@/hooks/use-live-data";
import { api, API_MODE } from "@/lib/api";
import { dateLongUtc, isOnline, plural, relativeTime, shortId, timeUtc } from "@/lib/format";
import { isNetworkIndicator } from "@/lib/indicators";
import type { EndpointAgent, RegisteredAgent } from "@/lib/types";
import { cn } from "@/lib/utils";

type Connectivity = "all" | "connected" | "offline" | "never";

const HOSTNAME = /^(?=.{1,253}$)[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)*$/;
const dayTime = (iso: string) =>
  `${new Date(iso).toLocaleDateString("en-GB", { day: "numeric", month: "short", timeZone: "UTC" })} · ${timeUtc(iso)}`;

function connectivityOf(agent: EndpointAgent, now: number): Exclude<Connectivity, "all"> {
  if (!agent.lastHeartbeatAt) return "never";
  return isOnline(agent.lastHeartbeatAt, now) ? "connected" : "offline";
}

const connectivityLabel = { connected: "Connected", offline: "Offline", never: "Never connected" } as const;

export function AgentsView() {
  const { agents, agentsError, reloadAgents, session } = useLive();
  const rules = useLiveRules();
  const now = useNow(2_000);
  const [query, setQuery] = useState("");
  const [filter, setFilter] = useState<Connectivity>("all");
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [registering, setRegistering] = useState(false);

  const list = useMemo(() => agents ?? [], [agents]);
  const online = list.filter((a) => connectivityOf(a, now) === "connected");
  const networkBlocks = (rules.data ?? []).filter((r) => r.listType === "BLACKLIST" && isNetworkIndicator(r.indicatorType));
  const filtered = list
    .filter((a) => filter === "all" || connectivityOf(a, now) === filter)
    .filter((a) => !query.trim() || a.hostname.includes(query.trim().toLowerCase()) || a.id.includes(query.trim().toLowerCase()));
  const selected = list.find((a) => a.id === selectedId) ?? online[0] ?? list[0] ?? null;

  return (
    <>
      <PageHeading
        title="Endpoint inventory"
        subtitle="Rust agents on Linux hosts · Connectivity and enforcement are separate states"
        actions={<Button onClick={() => setRegistering(true)}>Register agent</Button>}
      />

      {agents ? (
        online.length > 0 ? (
          <ContextNotice title="Endpoint daemon · CONNECTED">
            {plural(online.length, "agent")} sending heartbeats. Each one enforces the blacklist through the OBSIDIAN iptables chain and
            the /etc/hosts sinkhole, and reconciles against a full SYNC whenever it reconnects.
          </ContextNotice>
        ) : (
          <ContextNotice tone="warning" title="Endpoint daemon · NOT CONNECTED">
            No agent has sent a heartbeat in the last 30 seconds, so firewall and hosts-file enforcement are unavailable. Earlier
            heartbeats are not current connectivity.
          </ContextNotice>
        )
      ) : null}

      {agents ? (
        <div className="flex gap-4">
          <MetricCard label="Registered hosts" value={list.length} caption="Linux agents with a bearer token" />
          <MetricCard label="Connected agents" value={online.length} caption="Heartbeat in the last 30 s" />
          <MetricCard
            label="Network blocks"
            value={rules.data ? networkBlocks.length : "–"}
            caption="Domains and IPs pushed to every agent"
          />
        </div>
      ) : null}

      <div className="flex items-center gap-2.5">
        <label className="flex h-9 min-w-0 flex-1 items-center gap-[9px] rounded-md border border-line bg-surface px-3 focus-within:border-interactive focus-within:ring-1 focus-within:ring-interactive">
          <Image src="/icons/search.svg" alt="" width={16} height={16} unoptimized />
          <span className="sr-only">Search agents</span>
          <input
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="Search hostname or agent ID…"
            className="min-w-0 flex-1 bg-transparent text-[12px] leading-[1.4] text-ink outline-none placeholder:text-subtle focus-visible:outline-none"
          />
        </label>
        <Select value={filter} onValueChange={(v) => setFilter(v as Connectivity)}>
          <SelectTrigger aria-label="Connectivity">
            <SelectValue />
          </SelectTrigger>
          <SelectContent align="end">
            <SelectItem value="all">Connectivity</SelectItem>
            <SelectItem value="connected">Connected</SelectItem>
            <SelectItem value="offline">Offline</SelectItem>
            <SelectItem value="never">Never connected</SelectItem>
          </SelectContent>
        </Select>
      </div>

      <TablePanel
        title="Agents"
        meta={agents ? plural(filtered.length, "record") : undefined}
        footer={agents ? `Showing ${filtered.length} of ${plural(list.length, "agent")} · heartbeats every 10 s` : undefined}
      >
        {agentsError ? <ErrorNotice error={agentsError} onRetry={reloadAgents} /> : null}
        {!agents && !agentsError ? (
          <LoadingRows label="Loading agents…" />
        ) : filtered.length === 0 && agents ? (
          <EmptyState
            title={list.length ? "No agents in this view" : "No agents registered"}
            description={
              list.length
                ? "Broaden the search or clear the filter."
                : "Register a Linux host to get its bearer token, then start the daemon with it."
            }
            onClear={
              list.length
                ? () => {
                    setQuery("");
                    setFilter("all");
                  }
                : undefined
            }
          />
        ) : filtered.length > 0 ? (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Host / agent</TableHead>
                <TableHead>Registered</TableHead>
                <TableHead>Last heartbeat</TableHead>
                <TableHead>Connectivity</TableHead>
                <TableHead>Enforcement</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {filtered.map((agent) => {
                const state = connectivityOf(agent, now);
                return (
                  <TableRow
                    key={agent.id}
                    data-state={selected?.id === agent.id ? "selected" : undefined}
                    className="cursor-pointer hover:bg-canvas/60"
                    onClick={() => setSelectedId(agent.id)}
                  >
                    <TableCell>
                      <button
                        type="button"
                        className="block rounded-sm text-left text-ink"
                        aria-pressed={selected?.id === agent.id}
                        onClick={() => setSelectedId(agent.id)}
                      >
                        {agent.hostname}
                      </button>
                      <span className="block font-mono text-[11px]">{shortId("AGT", agent.id)}</span>
                    </TableCell>
                    <TableCell>{dayTime(agent.createdAt)}</TableCell>
                    <TableCell>
                      {agent.lastHeartbeatAt ? (
                        <>
                          <span className="block">{dayTime(agent.lastHeartbeatAt)}</span>
                          <span className="block">{relativeTime(agent.lastHeartbeatAt, now)}</span>
                        </>
                      ) : (
                        "Never"
                      )}
                    </TableCell>
                    <TableCell className={cn(state === "connected" && "text-verified", state === "offline" && "text-warning")}>
                      {connectivityLabel[state]}
                    </TableCell>
                    <TableCell className={state === "connected" ? "text-ink" : undefined}>
                      {state === "connected" ? "Active · iptables + /etc/hosts" : "Unavailable"}
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        ) : null}
      </TablePanel>

      <div className="flex items-start gap-5">
        <Panel className="flex-1">
          <PanelTitle>{selected ? `Selected host · ${selected.hostname}` : "Selected host"}</PanelTitle>
          {selected ? (
            <MetaList
              items={[
                ["Agent", <span key="id" className="font-mono text-[11px]">{selected.id}</span>],
                ["Registered", dayTime(selected.createdAt)],
                ["Last heartbeat", selected.lastHeartbeatAt ? relativeTime(selected.lastHeartbeatAt, now) : "Never"],
                ["Bearer token", "Shown once at registration · stored as a SHA-256 hash"],
              ]}
            />
          ) : (
            <p className="text-[12px] text-subtle">Register an agent to see its details.</p>
          )}
          {selected ? (
            <Link href={`/agents/${selected.id}`} className="w-fit text-[12px] leading-[1.4] text-interactive hover:underline">
              Open {selected.hostname} → enforced rules and activity
            </Link>
          ) : null}
        </Panel>
        <Panel className="flex-1">
          <PanelTitle>What connected means</PanelTitle>
          <p className="text-[12px] leading-[1.4] text-subtle">
            The agent authenticates to /ws/firewall with its bearer token and sends a heartbeat every 10 seconds. On every
            reconnect it receives the full rule SYNC and rebuilds the OBSIDIAN chain and its /etc/hosts lines, so rules added while
            it was offline still apply. Values are validated as IP addresses or strict domains before any system call.
          </p>
        </Panel>
      </div>

      <PageFootnote>
        {API_MODE === "mock" ? "Sample records" : "Live records"} · {dateLongUtc(new Date(now))} · UTC · Obsidian
        {session?.organization ? ` / ${session.organization.name}` : ""}
      </PageFootnote>

      {registering ? (
        <RegisterAgentDialog
          onClose={() => setRegistering(false)}
          onRegistered={(agent) => {
            reloadAgents();
            setSelectedId(agent.id);
          }}
        />
      ) : null}
    </>
  );
}

function RegisterAgentDialog({ onClose, onRegistered }: { onClose: () => void; onRegistered: (agent: RegisteredAgent) => void }) {
  const [hostname, setHostname] = useState("");
  const [touched, setTouched] = useState(false);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [agent, setAgent] = useState<RegisteredAgent | null>(null);
  const [copied, setCopied] = useState(false);
  const value = hostname.trim().toLowerCase();
  const invalid = touched && !HOSTNAME.test(value) ? "Enter a hostname: letters, digits, hyphens and dots only." : null;

  const register = async () => {
    setTouched(true);
    if (!HOSTNAME.test(value)) return;
    setPending(true);
    setError(null);
    try {
      const created = await api.registerAgent(value);
      setAgent(created);
      onRegistered(created);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Request failed.");
    } finally {
      setPending(false);
    }
  };

  return (
    <Dialog open onOpenChange={(open) => !open && !pending && onClose()}>
      <DialogContent>
        {agent ? (
          <>
            <DialogHeader>
              <DialogTitle>Agent registered · {agent.hostname}</DialogTitle>
              <DialogDescription>
                Copy the bearer token now. It is shown once; the server keeps only its SHA-256 hash.
              </DialogDescription>
            </DialogHeader>
            <div className="rounded-md border border-line bg-canvas p-3 font-mono text-[12px] leading-[1.5] break-all text-ink">
              {agent.token}
            </div>
            <ContextNotice tone="warning" title="Treat it like a password">
              Anyone with this token can receive this organization&apos;s rules as this host. Give it to the daemon as its bearer
              token and don&apos;t paste it into chat or tickets.
            </ContextNotice>
            <DialogFooter>
              <Button
                variant="outline"
                onClick={async () => {
                  await navigator.clipboard.writeText(agent.token);
                  setCopied(true);
                }}
              >
                {copied ? "Copied" : "Copy token"}
              </Button>
              <Button onClick={onClose}>Done</Button>
            </DialogFooter>
          </>
        ) : (
          <form
            className="grid gap-4"
            noValidate
            onSubmit={(event) => {
              event.preventDefault();
              void register();
            }}
          >
            <DialogHeader>
              <DialogTitle>Register agent</DialogTitle>
              <DialogDescription>Creates a bearer token for one Linux host running the Rust daemon.</DialogDescription>
            </DialogHeader>
            <div className="grid gap-[7px]">
              <Label htmlFor="agent-hostname">Hostname · required</Label>
              <Input
                id="agent-hostname"
                autoFocus
                value={hostname}
                autoComplete="off"
                spellCheck={false}
                placeholder="build-linux-01"
                aria-invalid={invalid ? true : undefined}
                aria-describedby="agent-hostname-help"
                onChange={(event) => setHostname(event.target.value)}
                onBlur={() => setTouched(true)}
              />
              <p id="agent-hostname-help" className={invalid ? "text-[11px] text-critical" : "text-[11px] text-subtle"}>
                {invalid ?? "Use the machine's hostname so heartbeats are easy to match."}
              </p>
            </div>
            {error ? (
              <ContextNotice tone="critical" title="Not registered">
                {error.slice(0, 200)}
              </ContextNotice>
            ) : null}
            <DialogFooter>
              <Button type="button" variant="outline" onClick={onClose} disabled={pending}>
                Cancel
              </Button>
              <Button type="submit" disabled={pending}>
                {pending ? "Registering…" : "Register and show token"}
              </Button>
            </DialogFooter>
          </form>
        )}
      </DialogContent>
    </Dialog>
  );
}
