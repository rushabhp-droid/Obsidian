import type { Metadata } from "next";
import { AdminOnly } from "@/components/obsidian/admin-only";
import { AuditRecordView } from "./audit-record-view";

export const metadata: Metadata = { title: "Audit Log" };

export default function AuditRecordPage() {
  return (
    <AdminOnly title="Audit record">
      <AuditRecordView />
    </AdminOnly>
  );
}
