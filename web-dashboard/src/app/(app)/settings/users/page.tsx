import type { Metadata } from "next";
import { AdminOnly } from "@/components/obsidian/admin-only";
import { UsersView } from "./users-view";

export const metadata: Metadata = { title: "Settings" };

export default function UsersSettingsPage() {
  return (
    <AdminOnly title="Users and roles">
      <UsersView />
    </AdminOnly>
  );
}
