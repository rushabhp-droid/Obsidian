"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { ContextNotice, MetaList, PageFootnote, PageHeading, Panel, PanelTitle } from "@/components/obsidian/panel";
import { SettingsTabs } from "@/components/obsidian/settings-tabs";
import { ErrorNotice, LoadingRows } from "@/components/obsidian/table-panel";
import { useLive, useNow } from "@/components/shell/live-provider";
import { useResource } from "@/hooks/use-resource";
import { api, API_MODE } from "@/lib/api";
import { dateLongUtc, plural } from "@/lib/format";

const loadOrg = () => api.orgSettings();

/** The organization's name and its employee bypass policy. Every change is audited. */
export function OrganizationView() {
  const org = useResource(loadOrg);
  const { session } = useLive();
  const now = useNow(60_000);
  const [name, setName] = useState("");
  const [bypass, setBypass] = useState<"allow" | "deny">("allow");
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (org.data) {
      setName(org.data.name);
      setBypass(org.data.policyAllowUserBypass ? "allow" : "deny");
    }
  }, [org.data]);

  const dirty = org.data && (name.trim() !== org.data.name || (bypass === "allow") !== org.data.policyAllowUserBypass);

  const save = async () => {
    setSaving(true);
    setError(null);
    setSaved(false);
    try {
      const updated = await api.updateOrg({ name: name.trim(), policyAllowUserBypass: bypass === "allow" });
      org.mutate(() => updated);
      setSaved(true);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Request failed.");
    } finally {
      setSaving(false);
    }
  };

  return (
    <>
      <PageHeading title="Organization settings" subtitle={`${org.data?.name ?? "Your organization"} · profile and access policy`} />
      <SettingsTabs />

      {org.error && !org.data ? <ErrorNotice error={org.error} onRetry={org.reload} /> : null}
      {!org.data && !org.error ? <LoadingRows label="Loading organization…" /> : null}

      {org.data ? (
        <>
          {error ? (
            <ContextNotice tone="critical" title="Not saved">
              {error.slice(0, 200)}
            </ContextNotice>
          ) : saved ? (
            <ContextNotice title="Saved">
              The change applies now and is recorded in the{" "}
              <Link href="/audit" className="text-interactive hover:underline">
                audit log
              </Link>
              .
            </ContextNotice>
          ) : null}

          <div className="flex items-start gap-5">
            <Panel className="flex-1">
              <PanelTitle>Organization profile</PanelTitle>
              <form
                className="flex flex-col gap-5"
                onSubmit={(event) => {
                  event.preventDefault();
                  if (dirty && name.trim()) void save();
                }}
              >
                <div className="flex flex-col gap-[7px]">
                  <Label htmlFor="org-name">Organization name</Label>
                  <Input id="org-name" value={name} maxLength={120} onChange={(event) => setName(event.target.value)} />
                  <p className="text-[11px] leading-[1.4] text-subtle">Shown in the sidebar and on every page footer.</p>
                </div>

                <div className="flex flex-col gap-[7px]">
                  <Label htmlFor="org-bypass">Employee bypass</Label>
                  <Select value={bypass} onValueChange={(value) => setBypass(value as typeof bypass)}>
                    <SelectTrigger id="org-bypass" className="w-full">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="allow">Allowed, with a time limit (1 minute to 24 hours)</SelectItem>
                      <SelectItem value="deny">Not allowed: only admins can lift a block</SelectItem>
                    </SelectContent>
                  </Select>
                  <p className="text-[11px] leading-[1.4] text-subtle">
                    Employees can never bypass an admin policy, and their bypasses expire on their own.
                  </p>
                </div>

                <Button type="submit" className="w-fit" disabled={!dirty || !name.trim() || saving}>
                  {saving ? "Saving…" : "Save changes"}
                </Button>
              </form>
            </Panel>

            <Panel className="flex-1">
              <PanelTitle>Organization facts</PanelTitle>
              <MetaList
                items={[
                  ["Members", plural(org.data.memberCount, "member")],
                  ["Admins", plural(org.data.adminCount, "admin")],
                  ["Created", `${dateLongUtc(new Date(org.data.createdAt))} · UTC`],
                  ["Organization ID", <span key="id" className="font-mono text-[11px]">{org.data.id}</span>],
                ]}
              />
              <Link href="/settings/users" className="w-fit text-[12px] leading-[1.4] text-interactive hover:underline">
                Users &amp; roles → add or remove members
              </Link>
              <Link href="/consumer-reports" className="w-fit text-[12px] leading-[1.4] text-interactive hover:underline">
                Consumer Reports → protected domains
              </Link>
            </Panel>
          </div>
        </>
      ) : null}

      <PageFootnote>
        {API_MODE === "mock" ? "Sample records" : "Live records"} · {dateLongUtc(new Date(now))} · UTC · Obsidian
        {session?.organization ? ` / ${session.organization.name}` : ""}
      </PageFootnote>
    </>
  );
}
