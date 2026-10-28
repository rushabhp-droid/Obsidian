import type { Metadata } from "next";
import { AdminOnly } from "@/components/obsidian/admin-only";
import { AuditView } from "./audit-view";

export const metadata: Metadata = { title: "Audit Log" };

export default function AuditPage() {
  return (
    <AdminOnly title="Audit log">
      <AuditView />
    </AdminOnly>
  );
}
