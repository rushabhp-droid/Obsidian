import type { Metadata } from "next";
import { AdminOnly } from "@/components/obsidian/admin-only";
import { AgentsView } from "./agents-view";

export const metadata: Metadata = { title: "Endpoint Security" };

export default function AgentsPage() {
  return (
    <AdminOnly title="Endpoint security">
      <AgentsView />
    </AdminOnly>
  );
}
