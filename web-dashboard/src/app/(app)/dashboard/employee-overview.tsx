"use client";

import Link from "next/link";
import { Badge } from "@/components/ui/badge";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { ContextNotice, MetaList, MetricCard, PageFootnote, PageHeading, Panel, PanelTitle } from "@/components/obsidian/panel";
import { ErrorNotice, LoadingRows, TablePanel } from "@/components/obsidian/table-panel";
import { VerdictText } from "@/components/obsidian/verdict";
import { useLive, useNow } from "@/components/shell/live-provider";
import { useLiveRules, useLiveScans } from "@/hooks/use-live-data";
import { useResource } from "@/hooks/use-resource";
import { api, API_MODE } from "@/lib/api";
import { channelLabel, dateLongUtc, plural, relativeTime, shortId, timeUtc } from "@/lib/format";

const DAY_MS = 86_400_000;
const loadGmail = () => api.gmailStatus();

/**
 * An employee's own workspace: their scans (the API returns only theirs), their
 * time-limited bypasses, and their connections. Nothing about colleagues.
 */
export function EmployeeOverview() {
  const scans = useLiveScans();
  const rules = useLiveRules();
  const gmail = useResource(loadGmail);
  const { session, events } = useLive();
  const now = useNow(10_000);

  const recent = (scans.data ?? []).filter((s) => now - new Date(s.createdAt).getTime() < DAY_MS);
  const flagged = recent.filter((s) => s.verdict !== "CLEAN");
  const malicious = flagged.filter((s) => s.verdict === "MALICIOUS");
  // An employee only sees their own personal rules; org and global rules have no user.
  const myBypasses = (rules.data ?? []).filter(
    (r) => r.userId !== null && r.listType === "WHITELIST" && (!r.expiresAt || new Date(r.expiresAt).getTime() > now),
  );
  const orgBlocks = (rules.data ?? []).filter((r) => r.userId === null && r.listType === "BLACKLIST");
  const firstName = session?.displayName.split(/\s+/)[0] ?? "";
  const ruleEvents = events.flatMap((e) => (e.type === "SCAN" ? [] : [e])).slice(0, 5);

  return (
    <>
      <PageHeading
        title="My security overview"
        subtitle={`${session?.displayName ?? ""} · ${session?.organization?.name ?? ""} · last 24 hours · UTC`}
      />

      <ContextNotice title={`Employee workspace${firstName ? ` · ${firstName}` : ""}`}>
        You see your own email and message scans and the rules that protect you. Organization-wide data is for admins.
        Report anything suspicious: one report blocks it for everyone in {session?.organization?.name ?? "your organization"}.
      </ContextNotice>

      <div className="flex gap-4">
        <MetricCard label="My threat events" value={scans.data ? flagged.length : "–"} caption="Flagged in the last 24 hours" />
        <MetricCard
          label="Malicious"
          value={scans.data ? malicious.length : "–"}
          tone={malicious.length > 0 ? "critical" : "neutral"}
          caption={malicious.length > 0 ? "Blocked or flagged for you" : "None in this window"}
        />
        <MetricCard
          label="My active bypasses"
          value={rules.data ? myBypasses.length : "–"}
          tone={myBypasses.length > 0 ? "warning" : "neutral"}
          caption="Each expires on its own"
        />
        <MetricCard
          label="Gmail"
          value={gmail.data ? (gmail.data.connected ? "On" : "Off") : "–"}
          caption={gmail.data?.connected ? (gmail.data.address ?? "Connected") : "Connect it in Settings"}
        />
      </div>

      <TablePanel
        title="My recent threat events"
        meta={scans.data ? plural(flagged.length, "event") : undefined}
        footer={
          <Link href="/threats" className="text-interactive hover:underline">
            All my threat events →
          </Link>
        }
      >
        {!scans.data ? (
          scans.error ? <ErrorNotice error={scans.error} onRetry={scans.reload} /> : <LoadingRows />
        ) : flagged.length === 0 ? (
          <p className="px-4 pb-4 text-[12px] text-subtle">
            Nothing flagged for you in the last 24 hours. That isn&apos;t a guarantee: keep reporting what looks wrong.
          </p>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Event</TableHead>
                <TableHead>Indicator</TableHead>
                <TableHead>Source</TableHead>
                <TableHead>Sender</TableHead>
                <TableHead>Time UTC</TableHead>
                <TableHead>Verdict</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {flagged.slice(0, 6).map((scan) => (
                <TableRow key={scan.id}>
                  <TableCell className="font-mono text-[11px] text-ink">{shortId("SCN", scan.id)}</TableCell>
                  <TableCell className="max-w-[280px] truncate" title={scan.indicatorChecked ?? undefined}>
                    {scan.indicatorChecked}
                  </TableCell>
                  <TableCell>{channelLabel[scan.channel]}</TableCell>
                  <TableCell className="max-w-[180px] truncate">{scan.sender}</TableCell>
                  <TableCell>{timeUtc(scan.createdAt)}</TableCell>
                  <TableCell>
                    <VerdictText verdict={scan.verdict} />
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </TablePanel>

      <div className="flex items-start gap-5">
        <Panel className="flex-1">
          <PanelTitle meta={rules.data ? plural(myBypasses.length, "bypass", "bypasses") : undefined}>My bypasses</PanelTitle>
          {!rules.data ? (
            <LoadingRows label="Loading rules…" />
          ) : myBypasses.length === 0 ? (
            <p className="text-[12px] leading-[1.6] text-subtle">
              You haven&apos;t bypassed anything. If a block stops your work, request a time-limited bypass in Rules and Policies.
            </p>
          ) : (
            <ul className="flex flex-col gap-3 text-[12px] leading-[1.4]">
              {myBypasses.map((rule) => (
                <li key={rule.id}>
                  <span className="block break-all text-ink">{rule.indicatorValue}</span>
                  <span className="block text-subtle">{rule.expiresAt ? `Expires ${relativeTime(rule.expiresAt, now)}` : "No expiry"}</span>
                </li>
              ))}
            </ul>
          )}
        </Panel>

        <Panel className="flex-1">
          <PanelTitle>Security notifications</PanelTitle>
          {ruleEvents.length === 0 ? (
            <p className="text-[12px] leading-[1.6] text-subtle">No rule changes since this page opened. New blocks appear here as they happen.</p>
          ) : (
            <ul className="flex flex-col gap-3 border-l-2 border-line pl-4 text-[12px] leading-[1.4]">
              {ruleEvents.map((event, index) => (
                <li key={`${event.ruleId}-${index}`}>
                  <span className="block text-ink">
                    {timeUtc(event.at)} / {event.type === "BLOCK" ? "Blocked" : "Bypassed"}
                  </span>
                  <span className="block break-all text-subtle">{event.value}</span>
                </li>
              ))}
            </ul>
          )}
        </Panel>

        <Panel className="flex-1">
          <PanelTitle>Protection around you</PanelTitle>
          <MetaList
            items={[
              ["Organization blocks", rules.data ? plural(orgBlocks.length, "rule") : "…"],
              ["Gmail scanning", gmail.data?.connected ? <Badge key="g" variant="verified">Connected</Badge> : "Not connected"],
              ["Bypass policy", session?.organization?.policyAllowUserBypass ? "Time-limited bypasses allowed" : "Only admins can bypass"],
            ]}
          />
          <Link href="/firewall" className="w-fit text-[12px] leading-[1.4] text-interactive hover:underline">
            Report a suspicious link or sender →
          </Link>
          {!gmail.data?.connected ? (
            <Link href="/settings" className="w-fit text-[12px] leading-[1.4] text-interactive hover:underline">
              Connect Gmail →
            </Link>
          ) : null}
        </Panel>
      </div>

      <PageFootnote>
        {API_MODE === "mock" ? "Sample records" : "Live records"} · {dateLongUtc(new Date(now))} · UTC · Obsidian
        {session?.organization ? ` / ${session.organization.name}` : ""}
      </PageFootnote>
    </>
  );
}
