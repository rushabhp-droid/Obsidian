import type { Metadata } from "next";
import { AdminOnly } from "@/components/obsidian/admin-only";
import { OrganizationView } from "./organization-view";

export const metadata: Metadata = { title: "Settings" };

export default function OrganizationSettingsPage() {
  return (
    <AdminOnly title="Organization settings">
      <OrganizationView />
    </AdminOnly>
  );
}
