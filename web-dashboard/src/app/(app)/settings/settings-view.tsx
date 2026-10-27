"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { ContextNotice, MetaList, PageFootnote, PageHeading, Panel, PanelTitle } from "@/components/obsidian/panel";
import { ErrorNotice, LoadingRows, TablePanel } from "@/components/obsidian/table-panel";
import { useLive, useNow } from "@/components/shell/live-provider";
import { useResource } from "@/hooks/use-resource";
import { useLiveScans } from "@/hooks/use-live-data";
import { api, API_MODE, GMAIL_CONNECT_URL } from "@/lib/api";
import { dateLongUtc, isOnline, plural, relativeTime } from "@/lib/format";
import { registerPasskey } from "@/lib/webauthn";
import { usePasskeySupport } from "@/hooks/use-passkey-support";
import { cn } from "@/lib/utils";

export function SettingsView() {
  const gmail = useResource(api.gmailStatus);
  const gmailResult = useSearchParams().get("gmail");
  const scans = useLiveScans();
  const { agents, agentsError, session, sessionError } = useLive();
  const now = useNow();
  const [disconnecting, setDisconnecting] = useState(false);

  const online = (agents ?? []).filter((a) => isOnline(a.lastHeartbeatAt, now));
  const latestHeartbeat = (agents ?? [])
    .map((a) => a.lastHeartbeatAt)
    .filter((t): t is string => t !== null)
    .sort()
    .at(-1);
  const latestMobile = (scans.data ?? []).find((s) => s.channel === "SMS" || s.channel === "WHATSAPP");
  const mobileRecent = latestMobile && now - new Date(latestMobile.createdAt).getTime() < 86_400_000;

  const disconnect = async () => {
    setDisconnecting(true);
    try {
      await api.disconnectGmail();
      gmail.reload();
    } finally {
      setDisconnecting(false);
    }
  };

  const issues = [
    gmail.data && !gmail.data.connected ? "Gmail is not connected, so new email isn't scanned" : null,
    agents && online.length === 0 ? "no agent is sending heartbeats, so network blocks aren't enforced" : null,
  ].filter(Boolean);

  return (
    <>
      <PageHeading title="Integration settings" subtitle="Connectivity, last sync and what each integration does are shown separately" />

      {gmail.data && agents ? (
        issues.length > 0 ? (
          <ContextNotice tone="warning" title="Some integrations need attention">
            {issues.join("; ")}.
          </ContextNotice>
        ) : (
          <ContextNotice title="All integrations connected">
            Gmail is polling and {plural(online.length, "agent")} {online.length === 1 ? "is" : "are"} enforcing the blacklist.
            Connected does not mean nothing malicious got through.
          </ContextNotice>
        )
      ) : null}

      {gmailResult === "not-configured" ? (
        <ContextNotice tone="warning" title="Gmail isn't set up on this server">
          Set GMAIL_CLIENT_ID and GMAIL_CLIENT_SECRET in .env from a Google OAuth client (type Web application), then
          restart the backend. The README&apos;s &quot;Connect Gmail&quot; section lists every step.
        </ContextNotice>
      ) : null}
      {gmailResult === "api-disabled" ? (
        <ContextNotice tone="warning" title="Enable the Gmail API, then connect again">
          Google signed you in, but the Gmail API is turned off in the Google Cloud project of this OAuth client.
          Enable it under APIs &amp; Services → Library → Gmail API, wait a few minutes and connect Gmail again.
        </ContextNotice>
      ) : null}
      {gmailResult === "error" ? (
        <ContextNotice tone="critical" title="Gmail was not connected">
          Google didn&apos;t grant access, or the sign-in expired. Try Connect Gmail again.
        </ContextNotice>
      ) : null}

      <TablePanel title="Integrations" meta="3 integrations">
        {gmail.error ? <ErrorNotice error={gmail.error} onRetry={gmail.reload} /> : null}
        {!gmail.data && !gmail.error ? (
          <LoadingRows label="Loading integrations…" />
        ) : (
          // Rows render with "Status unavailable" where a lookup failed.
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Integration</TableHead>
                <TableHead>Current state</TableHead>
                <TableHead>Last successful sync</TableHead>
                <TableHead>Operations</TableHead>
                <TableHead className="text-right">Setup</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              <TableRow>
                <TableCell className="text-ink">Gmail</TableCell>
                <TableCell className={cn(!gmail.data ? "text-warning" : gmail.data.connected ? "text-verified" : "text-warning")}>
                  <span className="block">{!gmail.data ? "Status unavailable" : gmail.data.connected ? "Connected" : "Not connected"}</span>
                  {gmail.data?.address ? <span className="block text-subtle">{gmail.data.address}</span> : null}
                </TableCell>
                <TableCell>{gmail.data?.connected ? relativeTime(gmail.data.lastPolledAt, now) : gmail.data ? "Never / not available" : "Unknown"}</TableCell>
                <TableCell>Polling, SPF/DKIM/DMARC, link and attachment scans</TableCell>
                <TableCell className="text-right">
                  {!gmail.data ? null : gmail.data.connected ? (
                    <Button variant="outline" size="sm" onClick={disconnect} disabled={disconnecting}>
                      {disconnecting ? "Disconnecting…" : "Disconnect"}
                    </Button>
                  ) : (
                    <Button size="sm" asChild>
                      {/* Spring's Gmail OAuth endpoint; a full navigation, not a client route. */}
                      <a href={GMAIL_CONNECT_URL}>Connect Gmail</a>
                    </Button>
                  )}
                </TableCell>
              </TableRow>
              <TableRow>
                <TableCell className="text-ink">Rust endpoint daemon</TableCell>
                <TableCell className={cn(online.length > 0 ? "text-verified" : "text-warning")}>
                  {agents
                    ? online.length > 0
                      ? `Connected · ${plural(online.length, "agent")}`
                      : "No heartbeat"
                    : agentsError
                      ? "Status unavailable"
                      : "Loading…"}
                </TableCell>
                <TableCell>{latestHeartbeat ? relativeTime(latestHeartbeat, now) : agents ? "Never / not available" : "Unknown"}</TableCell>
                <TableCell>Heartbeat, iptables and /etc/hosts enforcement</TableCell>
                <TableCell className="text-right">
                  <Button variant="outline" size="sm" asChild>
                    <Link href="/agents">Manage agents</Link>
                  </Button>
                </TableCell>
              </TableRow>
              <TableRow>
                <TableCell className="text-ink">Android app</TableCell>
                <TableCell className={cn(mobileRecent ? "text-ink" : "text-subtle")}>
                  {scans.data ? (mobileRecent ? "Reporting" : "No reports in 24 h") : scans.error ? "Status unavailable" : "Loading…"}
                </TableCell>
                <TableCell>{latestMobile ? relativeTime(latestMobile.createdAt, now) : scans.data ? "Never / not available" : "Unknown"}</TableCell>
                <TableCell>SMS and WhatsApp link scans, Report from the phone</TableCell>
                <TableCell className="text-right text-subtle">Set up on the phone</TableCell>
              </TableRow>
            </TableBody>
          </Table>
        )}
      </TablePanel>

      <div className="flex items-start gap-5">
        <Panel className="flex-1">
          <PanelTitle>Connectivity states</PanelTitle>
          <p className="text-[12px] leading-[1.4] text-ink">Connected · Authenticated and syncing; it does not imply enforcement or safety.</p>
          <p className="text-[12px] leading-[1.4] text-ink">No heartbeat · Registered, but nothing received in the last 30 seconds.</p>
          <p className="text-[12px] leading-[1.4] text-ink">Not connected · No connection established, so nothing is scanned or enforced.</p>
          <p className="text-[12px] leading-[1.4] text-subtle">
            Gmail stays in OAuth testing mode: every account has to be added as a test user before it can connect.
          </p>
        </Panel>
        <PasskeyPanel email={session?.email ?? (sessionError ? "Unavailable" : undefined)} />
      </div>

      <PageFootnote>
        {API_MODE === "mock" ? "Sample records" : "Live records"} · {dateLongUtc(new Date(now))} · UTC · Obsidian
        {session?.organization ? ` / ${session.organization.name}` : ""}
      </PageFootnote>
    </>
  );
}

function PasskeyPanel({ email }: { email: string | undefined }) {
  const [state, setState] = useState<"idle" | "pending" | "done" | "error">("idle");
  const [message, setMessage] = useState<string | null>(null);
  const canPasskey = usePasskeySupport();

  const add = async () => {
    setState("pending");
    setMessage(null);
    try {
      await registerPasskey(`${navigator.platform || "This device"} · ${new Date().toISOString().slice(0, 10)}`);
      setState("done");
    } catch (error) {
      setState("error");
      setMessage(
        error instanceof DOMException && error.name === "NotAllowedError"
          ? "Passkey creation was cancelled."
          : "The passkey could not be saved. Try again.",
      );
    }
  };

  return (
    <Panel className="flex-1">
      <PanelTitle>Passkeys</PanelTitle>
      <MetaList
        items={[
          ["Account", email ?? "Loading…"],
          ["Sign-in methods", "Passkey · password (Argon2id)"],
          ["Relying party", "This hostname · passkeys don't work on a quick tunnel URL"],
        ]}
      />
      {state === "done" ? (
        <ContextNotice title="Passkey added">You can now sign in with it from the sign-in page.</ContextNotice>
      ) : null}
      {state === "error" && message ? (
        <ContextNotice tone="critical" title="Not added">
          {message}
        </ContextNotice>
      ) : null}
      <Button
        variant="outline"
        className="w-fit"
        onClick={add}
        disabled={state === "pending" || (API_MODE === "live" && !canPasskey)}
      >
        {state === "pending" ? "Waiting for passkey…" : "Add a passkey"}
      </Button>
    </Panel>
  );
}
