"use client";

import { LoadingRows } from "@/components/obsidian/table-panel";
import { useLive } from "@/components/shell/live-provider";
import { DashboardView } from "./dashboard-view";
import { EmployeeOverview } from "./employee-overview";

/** Sign-in lands everyone on /dashboard: admins get the organization, employees their own workspace. */
export function OverviewSwitch() {
  const { session } = useLive();
  if (!session) return <LoadingRows label="Loading overview…" />;
  return session.role === "ADMIN" ? <DashboardView /> : <EmployeeOverview />;
}
