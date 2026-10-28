"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useCallback } from "react";
import { Badge } from "@/components/ui/badge";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { ContextNotice, MetaList, PageFootnote, PageHeading, Panel, PanelTitle } from "@/components/obsidian/panel";
import { EmptyState, ErrorNotice, LoadingRows, TablePanel } from "@/components/obsidian/table-panel";
import { VerdictText } from "@/components/obsidian/verdict";
import { useLive, useNow } from "@/components/shell/live-provider";
import { useLiveScans } from "@/hooks/use-live-data";
import { useResource } from "@/hooks/use-resource";
import { api, API_MODE } from "@/lib/api";
import { dateLongUtc, indicatorTypeLabel, isOnline, plural, relativeTime, shortId, timeUtc } from "@/lib/format";

/** One Rust daemon: its connection, the block set it enforces, and the firewall events it reported. */
export function EndpointDetailView() {
  const { id } = useParams<{ id: string }>();
  const load = useCallback(() => api.agentDetail(id), [id]);
  const detail = useResource(load);
  const scans = useLiveScans();
  const { agents, session } = useLive();
  const now = useNow();

  if (detail.error && !detail.data) {
    return (
      <>
        <PageHeading title="Endpoint" subtitle={shortId("AGT", id)} />
        <ErrorNotice error={detail.error} onRetry={detail.reload} />
        <Link href="/agents" className="text-[12px] text-interactive hover:underline">
          ← Back to Endpoint Security
        </Link>
      </>
    );
  }
  if (!detail.data) return <LoadingRows label="Loading endpoint…" />;

  // The socket keeps heartbeats fresher than this page's first load.
  const agent = agents?.find((a) => a.id === id) ?? detail.data.agent;
  const online = isOnline(agent.lastHeartbeatAt, now);
  const enforced = detail.data.enforced;
  const domains = enforced.filter((r) => r.indicatorType === "DOMAIN");
  const ips = enforced.filter((r) => r.indicatorType === "IP");
  const events = (scans.data ?? []).filter((s) => s.channel === "FIREWALL" && s.sender === agent.hostname);

  return (
    <>
      <PageHeading
        title={agent.hostname}
        subtitle={`${shortId("AGT", agent.id)} · Linux host · registered ${dateLongUtc(new Date(agent.createdAt))}`}
        actions={online ? <Badge variant="verified">Connected</Badge> : <Badge variant="warning">No heartbeat</Badge>}
      />

      <div className="flex items-start gap-5">
        <Panel className="flex-1">
          <PanelTitle>Rust daemon state</PanelTitle>
          <MetaList
            items={[
              ["Daemon", online ? "Connected to /ws/firewall" : agent.lastHeartbeatAt ? "Not connected" : "Never connected"],
              ["Agent", <span key="id" className="font-mono text-[11px]">{agent.id}</span>],
              ["Registered", `${dateLongUtc(new Date(agent.createdAt))} · ${timeUtc(agent.createdAt)}`],
              ["Last heartbeat", agent.lastHeartbeatAt ? `${relativeTime(agent.lastHeartbeatAt, now)} · ${timeUtc(agent.lastHeartbeatAt)}` : "Never"],
              ["Heartbeat interval", "10 s · offline after 30 s without one"],
              ["Bearer token", "Shown once at registration · stored as a SHA-256 hash"],
            ]}
          />
        </Panel>
        <Panel className="flex-1">
          <PanelTitle>Policy &amp; enforcement</PanelTitle>
          <MetaList
            items={[
              ["Block set", `${plural(domains.length, "domain")} and ${plural(ips.length, "IP address", "IP addresses")}`],
              ["Source", "Organization rules + global blocks, after precedence"],
              ["Firewall", online ? "iptables OBSIDIAN chain · active" : "Unavailable while disconnected"],
              ["Hosts file", online ? "/etc/hosts sinkhole · active" : "Unavailable while disconnected"],
              ["On reconnect", "Full SYNC: rules added while offline still apply"],
            ]}
          />
        </Panel>
      </div>

      {!online ? (
        <ContextNotice tone="warning" title="Not enforcing right now">
          {agent.lastHeartbeatAt
            ? `The daemon's last heartbeat was ${relativeTime(agent.lastHeartbeatAt, now)}. Its last applied rules stay in place on the host, but new blocks reach it only after it reconnects.`
            : "This agent has never connected. Install the daemon on the host with its token, then it receives the block set below."}
        </ContextNotice>
      ) : null}

      <TablePanel
        title="Enforced block set"
        meta={plural(enforced.length, "rule")}
        footer="What the daemon receives on SYNC. URLs, senders and file hashes are applied by the scanners, not the firewall."
      >
        {enforced.length === 0 ? (
          <EmptyState title="Nothing to block yet" description="Report a domain or IP address in Rules and Policies; connected agents block it in under a second." />
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Indicator</TableHead>
                <TableHead>Type</TableHead>
                <TableHead>Applied as</TableHead>
                <TableHead className="text-right">Rule</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {enforced.map((rule) => (
                <TableRow key={rule.ruleId}>
                  <TableCell className="font-mono text-[12px] text-ink">{rule.value}</TableCell>
                  <TableCell>{indicatorTypeLabel[rule.indicatorType]}</TableCell>
                  <TableCell>{rule.indicatorType === "DOMAIN" ? "/etc/hosts sinkhole" : "iptables DROP"}</TableCell>
                  <TableCell className="text-right">
                    <Link href={`/firewall?q=${encodeURIComponent(rule.value)}`} className="font-mono text-[11px] text-interactive hover:underline">
                      {shortId("RUL", rule.ruleId)}
                    </Link>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </TablePanel>

      <TablePanel title="Related event history" meta={scans.data ? plural(events.length, "event") : undefined}>
        {!scans.data ? (
          scans.error ? <ErrorNotice error={scans.error} onRetry={scans.reload} /> : <LoadingRows />
        ) : events.length === 0 ? (
          <p className="px-4 pb-4 text-[12px] text-subtle">No network events from this host among the latest scans.</p>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Event</TableHead>
                <TableHead>Time UTC</TableHead>
                <TableHead>Indicator</TableHead>
                <TableHead>VirusTotal</TableHead>
                <TableHead>Verdict</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {events.map((scan) => (
                <TableRow key={scan.id}>
                  <TableCell className="font-mono text-[11px] text-ink">{shortId("SCN", scan.id)}</TableCell>
                  <TableCell>{timeUtc(scan.createdAt)}</TableCell>
                  <TableCell className="max-w-[320px] truncate">{scan.indicatorChecked}</TableCell>
                  <TableCell>{scan.verdict === "PENDING" ? "Queued" : plural(scan.vtPositives, "detection")}</TableCell>
                  <TableCell>
                    <VerdictText verdict={scan.verdict} />
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </TablePanel>

      <Link href="/agents" className="w-fit text-[12px] leading-[1.4] text-interactive hover:underline">
        ← All endpoints
      </Link>

      <PageFootnote>
        {API_MODE === "mock" ? "Sample records" : "Live records"} · {dateLongUtc(new Date(now))} · UTC · Obsidian
        {session?.organization ? ` / ${session.organization.name}` : ""}
      </PageFootnote>
    </>
  );
}
