"use client";

import { useState } from "react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { ContextNotice, PageFootnote, PageHeading, Panel, PanelTitle } from "@/components/obsidian/panel";
import { SettingsTabs } from "@/components/obsidian/settings-tabs";
import { ErrorNotice, LoadingRows, TablePanel } from "@/components/obsidian/table-panel";
import { useLive, useNow } from "@/components/shell/live-provider";
import { useResource } from "@/hooks/use-resource";
import { api, API_MODE } from "@/lib/api";
import { dateLongUtc, plural } from "@/lib/format";
import type { Member } from "@/lib/types";

type MemberRole = Member["role"];
const roleLabel: Record<MemberRole, string> = { ADMIN: "Organization admin", EMPLOYEE: "Employee" };
const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const loadMembers = () => api.listMembers();

/** What each role can do, as the server enforces it today. */
const CAPABILITIES: [string, string, string][] = [
  ["Overview, threat monitoring, reports", "Whole organization", "Own scans only"],
  ["Report an indicator", "Blocks for the organization (admin policy)", "Blocks for the organization"],
  ["Bypass a block", "Any duration", "Time-limited, if the organization allows it"],
  ["Endpoints, Code Security, consumer reports", "Allowed", "Not allowed"],
  ["Audit log, members, organization settings", "Allowed", "Not allowed"],
];

/** Members, their roles, and adding or removing them. Changes apply on the member's next request. */
export function UsersView() {
  const members = useResource(loadMembers);
  const { session } = useLive();
  const now = useNow(60_000);
  const [pending, setPending] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [confirm, setConfirm] = useState<{ member: Member; role?: MemberRole; remove?: boolean } | null>(null);
  const [added, setAdded] = useState<{ member: Member; password: string } | null>(null);

  const list = members.data ?? [];
  const admins = list.filter((m) => m.role === "ADMIN").length;

  const apply = async () => {
    if (!confirm) return;
    const { member, role, remove } = confirm;
    setConfirm(null);
    setPending(member.id);
    setActionError(null);
    try {
      if (remove) {
        await api.removeMember(member.id);
        members.mutate((current = []) => current.filter((m) => m.id !== member.id));
      } else if (role) {
        const updated = await api.setMemberRole(member.id, role);
        members.mutate((current = []) => current.map((m) => (m.id === updated.id ? updated : m)));
      }
    } catch (e) {
      setActionError(e instanceof Error ? e.message : "Request failed.");
    } finally {
      setPending(null);
    }
  };

  return (
    <>
      <PageHeading title="Users and roles" subtitle="Organization membership and least-privilege access" />
      <SettingsTabs />

      <ContextNotice title="Enforced by the server">
        Roles decide what the API returns, not just what the menu shows. A role change or removal applies on the member&apos;s
        next request, and every change is written to the audit log.
      </ContextNotice>

      {actionError ? (
        <ContextNotice tone="critical" title="Not changed">
          {actionError.slice(0, 200)}
        </ContextNotice>
      ) : null}

      {added ? (
        <ContextNotice tone="warning" title={`${added.member.displayName} can sign in now`}>
          Give them their temporary password in person or over a trusted channel: it isn&apos;t shown again.{" "}
          <span className="font-mono text-ink">{added.password}</span> · They sign in at the dashboard with{" "}
          {added.member.email} and can add a passkey afterwards.
        </ContextNotice>
      ) : null}

      <TablePanel title="Members" meta={members.data ? `${plural(list.length, "member")} · ${plural(admins, "admin")}` : undefined}>
        {members.error ? <ErrorNotice error={members.error} onRetry={members.reload} /> : null}
        {!members.data && !members.error ? (
          <LoadingRows label="Loading members…" />
        ) : members.data ? (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Member</TableHead>
                <TableHead>Email</TableHead>
                <TableHead>Role</TableHead>
                <TableHead>Sign-in</TableHead>
                <TableHead>Gmail</TableHead>
                <TableHead>Joined</TableHead>
                <TableHead className="text-right">Action</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {list.map((member) => {
                const lastAdmin = member.role === "ADMIN" && admins <= 1;
                return (
                  <TableRow key={member.id}>
                    <TableCell className="text-ink">
                      {member.displayName}
                      {member.you ? <Badge className="ml-2">You</Badge> : null}
                    </TableCell>
                    <TableCell>{member.email}</TableCell>
                    <TableCell>
                      <Select
                        value={member.role}
                        disabled={pending === member.id || lastAdmin}
                        onValueChange={(value) => setConfirm({ member, role: value as MemberRole })}
                      >
                        <SelectTrigger aria-label={`Role of ${member.displayName}`} className="w-[170px]">
                          <SelectValue />
                        </SelectTrigger>
                        <SelectContent>
                          <SelectItem value="EMPLOYEE">{roleLabel.EMPLOYEE}</SelectItem>
                          <SelectItem value="ADMIN">{roleLabel.ADMIN}</SelectItem>
                        </SelectContent>
                      </Select>
                    </TableCell>
                    <TableCell>{member.passkeys > 0 ? `Passkey (${member.passkeys}) + password` : "Password"}</TableCell>
                    <TableCell>{member.gmailConnected ? "Connected" : "—"}</TableCell>
                    <TableCell>{dateLongUtc(new Date(member.createdAt))}</TableCell>
                    <TableCell className="text-right">
                      <Button
                        size="sm"
                        variant="outline"
                        disabled={member.you || lastAdmin || pending === member.id}
                        title={member.you ? "You can't remove your own account" : lastAdmin ? "The last admin can't be removed" : undefined}
                        onClick={() => setConfirm({ member, remove: true })}
                      >
                        Remove
                      </Button>
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        ) : null}
      </TablePanel>

      <div className="flex items-start gap-5">
        <AddMember
          onAdded={(member, password) => {
            members.mutate((current = []) => [...current, member]);
            setAdded({ member, password });
          }}
        />
        <TablePanel title="Role capabilities" meta="as enforced" className="flex-1">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Capability</TableHead>
                <TableHead>Organization admin</TableHead>
                <TableHead>Employee</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {CAPABILITIES.map(([capability, admin, employee]) => (
                <TableRow key={capability}>
                  <TableCell className="text-ink">{capability}</TableCell>
                  <TableCell>{admin}</TableCell>
                  <TableCell>{employee}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TablePanel>
      </div>

      <PageFootnote>
        {API_MODE === "mock" ? "Sample records" : "Live records"} · {dateLongUtc(new Date(now))} · UTC · Obsidian
        {session?.organization ? ` / ${session.organization.name}` : ""}
      </PageFootnote>

      <Dialog open={confirm !== null} onOpenChange={(open) => !open && setConfirm(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>
              {confirm?.remove
                ? `Remove ${confirm.member.displayName}?`
                : confirm?.role === "ADMIN"
                  ? `Make ${confirm?.member.displayName} an organization admin?`
                  : `Make ${confirm?.member.displayName} an employee?`}
            </DialogTitle>
            <DialogDescription>
              {confirm?.remove
                ? "Their account, passkeys and personal rules are deleted, and they're signed out on their next request. Rules they reported for the organization stay."
                : confirm?.role === "ADMIN"
                  ? "Admins see the whole organization, manage members and settings, read the audit log, and their reports and bypasses become organization policy."
                  : "They'll see only their own scans and can't manage members, endpoints or settings."}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setConfirm(null)}>
              Cancel
            </Button>
            <Button variant={confirm?.remove ? "destructive" : "default"} onClick={() => void apply()}>
              {confirm?.remove ? "Remove member" : "Change role"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}

function generatePassword() {
  const alphabet = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  const bytes = crypto.getRandomValues(new Uint8Array(16));
  return Array.from(bytes, (b) => alphabet[b % alphabet.length]).join("");
}

function AddMember({ onAdded }: { onAdded: (member: Member, password: string) => void }) {
  const [email, setEmail] = useState("");
  const [name, setName] = useState("");
  const [role, setRole] = useState<MemberRole>("EMPLOYEE");
  const [password, setPassword] = useState(generatePassword);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [touched, setTouched] = useState(false);

  const emailError = touched && !EMAIL.test(email.trim()) ? "Enter a work email such as name@company.com." : null;
  const passwordError = touched && password.length < 12 ? "At least 12 characters." : null;

  const submit = async () => {
    setTouched(true);
    if (!EMAIL.test(email.trim()) || password.length < 12) return;
    setSaving(true);
    setError(null);
    try {
      const member = await api.addMember({ email: email.trim(), displayName: name.trim(), role, temporaryPassword: password });
      onAdded(member, password);
      setEmail("");
      setName("");
      setRole("EMPLOYEE");
      setPassword(generatePassword());
      setTouched(false);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Request failed.");
    } finally {
      setSaving(false);
    }
  };

  return (
    <Panel className="w-[420px] shrink-0">
      <PanelTitle>Add a member</PanelTitle>
      <form
        className="flex flex-col gap-4"
        onSubmit={(event) => {
          event.preventDefault();
          void submit();
        }}
      >
        <div className="flex flex-col gap-[7px]">
          <Label htmlFor="member-email">Work email · required</Label>
          <Input id="member-email" type="email" value={email} placeholder="name@company.com" onChange={(e) => setEmail(e.target.value)} aria-invalid={emailError !== null} />
          {emailError ? <p className="text-[11px] text-critical">{emailError}</p> : null}
        </div>
        <div className="flex flex-col gap-[7px]">
          <Label htmlFor="member-name">Name</Label>
          <Input id="member-name" value={name} maxLength={120} onChange={(e) => setName(e.target.value)} />
        </div>
        <div className="flex flex-col gap-[7px]">
          <Label htmlFor="member-role">Role</Label>
          <Select value={role} onValueChange={(value) => setRole(value as MemberRole)}>
            <SelectTrigger id="member-role" className="w-full">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="EMPLOYEE">Employee · least privilege</SelectItem>
              <SelectItem value="ADMIN">Organization admin</SelectItem>
            </SelectContent>
          </Select>
        </div>
        <div className="flex flex-col gap-[7px]">
          <Label htmlFor="member-password">Temporary password</Label>
          <div className="flex gap-2">
            <Input id="member-password" value={password} onChange={(e) => setPassword(e.target.value)} className="font-mono" aria-invalid={passwordError !== null} />
            <Button type="button" variant="outline" onClick={() => setPassword(generatePassword())}>
              New
            </Button>
          </div>
          <p className={passwordError ? "text-[11px] text-critical" : "text-[11px] text-subtle"}>
            {passwordError ?? "Shown once after adding. Hand it over in person; there's no invitation email."}
          </p>
        </div>
        {error ? <p className="text-[12px] text-critical">{error.slice(0, 200)}</p> : null}
        <Button type="submit" className="w-fit" disabled={saving}>
          {saving ? "Adding…" : "Add member"}
        </Button>
      </form>
    </Panel>
  );
}
