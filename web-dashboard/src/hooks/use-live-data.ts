"use client";

import { useEffect } from "react";
import { api } from "@/lib/api";
import type { ScanLog } from "@/lib/types";
import { useLive } from "@/components/shell/live-provider";
import { useResource } from "./use-resource";

/** Scan logs with SCAN events from /ws/dashboard merged in, newest first. */
export function useLiveScans() {
  const scans = useResource(api.listScans);
  const { events } = useLive();
  const { mutate } = scans;

  useEffect(() => {
    const incoming = events.flatMap((event) => (event.type === "SCAN" ? [event.scan] : []));
    if (incoming.length === 0) return;
    mutate((list = []) => {
      const seen = new Set(incoming.map((scan) => scan.id));
      return [...incoming, ...list.filter((scan: ScanLog) => !seen.has(scan.id))];
    });
  }, [events, mutate]);

  return scans;
}

/** Rule list that refetches whenever a BLOCK or BYPASS event arrives. */
export function useLiveRules() {
  const rules = useResource(api.listRules);
  const { ruleVersion } = useLive();
  const { reload } = rules;

  useEffect(() => {
    if (ruleVersion > 0) reload();
  }, [ruleVersion, reload]);

  return rules;
}
