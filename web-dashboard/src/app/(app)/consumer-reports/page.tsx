import type { Metadata } from "next";
import { ConsumerReportsView } from "./consumer-reports-view";

export const metadata: Metadata = { title: "Consumer Reports" };

export default function ConsumerReportsPage() {
  return <ConsumerReportsView />;
}
