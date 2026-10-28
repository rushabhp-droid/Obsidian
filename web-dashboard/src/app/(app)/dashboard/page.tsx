import type { Metadata } from "next";
import { OverviewSwitch } from "./overview-switch";

export const metadata: Metadata = { title: "Overview" };

export default function DashboardPage() {
  return <OverviewSwitch />;
}
