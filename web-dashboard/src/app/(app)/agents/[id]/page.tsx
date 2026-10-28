import type { Metadata } from "next";
import { AdminOnly } from "@/components/obsidian/admin-only";
import { EndpointDetailView } from "./endpoint-detail-view";

export const metadata: Metadata = { title: "Endpoint Security" };

export default function EndpointDetailPage() {
  return (
    <AdminOnly title="Endpoint security">
      <EndpointDetailView />
    </AdminOnly>
  );
}
