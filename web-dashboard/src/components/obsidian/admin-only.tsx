"use client";

import Link from "next/link";
import { ContextNotice, PageHeading } from "@/components/obsidian/panel";
import { LoadingRows } from "@/components/obsidian/table-panel";
import { useLive } from "@/components/shell/live-provider";

/**
 * Renders an admin page only for organization admins. The API refuses employees
 * anyway (403); this keeps them from landing on a page of errors.
 */
export function AdminOnly({ title, children }: { title: string; children: React.ReactNode }) {
  const { session } = useLive();
  if (!session) return <LoadingRows label="Loading…" />;
  if (session.role === "ADMIN") return <>{children}</>;
  return (
    <>
      <PageHeading title={title} />
      <ContextNotice tone="warning" title="This page is for organization admins">
        Ask an admin of {session.organization?.name ?? "your organization"} if you need this information.{" "}
        <Link href="/dashboard" className="text-interactive hover:underline">
          Back to your overview →
        </Link>
      </ContextNotice>
    </>
  );
}
