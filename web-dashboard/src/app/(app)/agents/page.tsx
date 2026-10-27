import type { Metadata } from "next";
import { AgentsView } from "./agents-view";

export const metadata: Metadata = { title: "Endpoint Security" };

export default function AgentsPage() {
  return <AgentsView />;
}
