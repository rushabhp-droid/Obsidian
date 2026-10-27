"use client";

import { createContext, useContext, useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { api, ApiError } from "@/lib/api";
import { registerDashboardTab } from "@/lib/dashboard-tab-session";
import { subscribeDashboard } from "@/lib/ws";
import { useResource } from "@/hooks/use-resource";
import type { DashboardEvent, EndpointAgent, SessionInfo } from "@/lib/types";

interface LiveState {
  session: SessionInfo | undefined;
  sessionError: Error | undefined;
  /** /ws/dashboard is open. */
  socketConnected: boolean;
  /** Rule and scan events received since the page loaded, newest first. */
  events: Exclude<DashboardEvent, { type: "HEARTBEAT" } | { type: "CODE_FINDING" }>[];
  /** Increments on every BLOCK / BYPASS event, so rule lists know to refetch. */
  ruleVersion: number;
  /** Increments on every Code Guard finding (admins only). */
  codeVersion: number;
  /** Agents with heartbeats from the socket merged in. */
  agents: EndpointAgent[] | undefined;
  agentsError: Error | undefined;
  reloadAgents: () => void;
}

const LiveContext = createContext<LiveState | null>(null);

/** Session, agent heartbeats and the dashboard socket, shared by the shell and pages. */
export function LiveProvider({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const [tabReady, setTabReady] = useState(false);
  const session = useResource(api.session, tabReady);
  const agents = useResource(api.listAgents, tabReady);
  const [events, setEvents] = useState<LiveState["events"]>([]);
  const [socketConnected, setSocketConnected] = useState(false);
  const [ruleVersion, setRuleVersion] = useState(0);
  const [codeVersion, setCodeVersion] = useState(0);
  const { mutate: mutateAgents } = agents;

  useEffect(() => {
    const tab = registerDashboardTab();
    if (tab.needsLogin) {
      void api.logout()
        .catch(() => undefined)
        .finally(() => router.replace("/login?reason=expired"));
      return tab.dispose;
    }
    setTabReady(true);
    return tab.dispose;
  }, [router]);

  useEffect(() => {
    if (session.error instanceof ApiError && session.error.status === 401) {
      router.replace("/login?reason=expired");
    }
  }, [session.error, router]);

  useEffect(
    () => {
      if (!tabReady) return;
      return subscribeDashboard((event) => {
        if (event.type === "HEARTBEAT") {
          mutateAgents((list = []) =>
            list.map((a) => (a.id === event.agentId ? { ...a, lastHeartbeatAt: event.at } : a)),
          );
        } else if (event.type === "CODE_FINDING") {
          setCodeVersion((v) => v + 1);
        } else {
          setEvents((list) => [event, ...list].slice(0, 25));
          if (event.type !== "SCAN") setRuleVersion((v) => v + 1);
        }
      }, setSocketConnected);
    },
    [mutateAgents, tabReady],
  );

  const value = useMemo<LiveState>(
    () => ({
      session: session.data,
      sessionError: session.error,
      socketConnected,
      events,
      ruleVersion,
      codeVersion,
      agents: agents.data,
      agentsError: agents.error,
      reloadAgents: agents.reload,
    }),
    [session.data, session.error, socketConnected, events, ruleVersion, codeVersion, agents.data, agents.error, agents.reload],
  );

  return <LiveContext.Provider value={value}>{tabReady ? children : null}</LiveContext.Provider>;
}

export function useLive() {
  const value = useContext(LiveContext);
  if (!value) throw new Error("useLive must be used inside <LiveProvider>");
  return value;
}

/** Re-renders on an interval so "4 s ago" and online/offline stay current. */
export function useNow(intervalMs = 5_000) {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), intervalMs);
    return () => clearInterval(timer);
  }, [intervalMs]);
  return now;
}
