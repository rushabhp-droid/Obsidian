"use client";

import Image from "next/image";
import { usePathname, useRouter } from "next/navigation";
import { useState } from "react";
import { ChevronDownIcon } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { api, API_MODE } from "@/lib/api";
import { channelLabel, isOnline, timeUtc } from "@/lib/format";
import { sectionLabel } from "./nav";
import { useLive, useNow } from "./live-provider";

/** 64 px context header: breadcrumb, agent status, search, live events, account. */
export function ContextHeader() {
  const pathname = usePathname();
  const { session } = useLive();
  const workspace = session?.organization?.name ?? session?.displayName ?? "";

  return (
    <header className="sticky top-0 z-30 flex h-16 shrink-0 items-center justify-between border-b border-line bg-surface px-8">
      <p className="text-[12px] leading-[1.4] whitespace-pre text-subtle">
        {workspace ? `${workspace}  /  ` : ""}
        {sectionLabel(pathname, session?.role)}
      </p>
      <AgentStatus />
      <SearchRules />
      <LiveEvents />
      <AccountMenu />
    </header>
  );
}

function AgentStatus() {
  const { agents, agentsError } = useLive();
  const now = useNow();
  if (!agents) {
    return agentsError ? (
      <p className="text-[11px] leading-[1.4] text-warning" role="status">
        Agent status unavailable
      </p>
    ) : (
      <span className="w-40" aria-hidden />
    );
  }
  const online = agents.filter((a) => isOnline(a.lastHeartbeatAt, now));
  const prefix = API_MODE === "mock" ? "Sample data · " : "";
  return (
    <p className="flex items-center gap-1.5 text-[11px] leading-[1.4] text-subtle" role="status">
      {online.length > 0 ? (
        <>
          <span className="size-1.5 rounded-full bg-verified" aria-hidden />
          {prefix}
          <span className="text-verified">Rust agent connected</span>
          {` · ${online.length === 1 ? online[0].hostname : `${online.length} hosts`}`}
        </>
      ) : (
        <>
          <span className="size-1.5 rounded-full bg-subtle" aria-hidden />
          {prefix}No agent connected
        </>
      )}
    </p>
  );
}

function SearchRules() {
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState("");

  return (
    <>
      <Button variant="ghost" size="icon-sm" aria-label="Search rules" onClick={() => setOpen(true)}>
        <Image src="/icons/search.svg" alt="" width={16} height={16} unoptimized />
      </Button>
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent>
          <form
            className="grid gap-4"
            onSubmit={(event) => {
              event.preventDefault();
              setOpen(false);
              router.push(`/firewall?q=${encodeURIComponent(query.trim().toLowerCase())}`);
            }}
          >
            <DialogHeader>
              <DialogTitle>Search rules</DialogTitle>
              <DialogDescription>Find a domain, URL, IP, sender or file hash in the blacklist and whitelist.</DialogDescription>
            </DialogHeader>
            <div className="grid gap-[7px]">
              <Label htmlFor="rule-search">Indicator</Label>
              <Input
                id="rule-search"
                autoFocus
                value={query}
                onChange={(event) => setQuery(event.target.value)}
                placeholder="testsafebrowsing.appspot.com"
              />
            </div>
            <DialogFooter>
              <Button type="submit" disabled={!query.trim()}>
                Search
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </>
  );
}

function LiveEvents() {
  const { events, socketConnected } = useLive();

  return (
    <Popover>
      <PopoverTrigger asChild>
        <Button variant="ghost" size="icon-sm" aria-label={`Live events (${events.length})`} className="relative">
          <Image src="/icons/bell.svg" alt="" width={16} height={16} unoptimized />
          {events.length > 0 ? (
            <span className="absolute top-1.5 right-1.5 size-1.5 rounded-full bg-interactive" aria-hidden />
          ) : null}
        </Button>
      </PopoverTrigger>
      <PopoverContent align="end" className="w-80 gap-3 p-4">
        <p className="text-[15px] font-semibold">Live events</p>
        {events.length === 0 ? (
          <p className="text-[12px] text-subtle">
            {socketConnected
              ? "No rule changes or scan verdicts since this page opened."
              : "The live feed is disconnected. Reconnecting…"}
          </p>
        ) : (
          <ul className="flex max-h-80 flex-col gap-3 overflow-y-auto border-l-2 border-line pl-4 text-[12px] leading-[1.6] text-subtle">
            {events.map((event, index) =>
              event.type === "SCAN" ? (
                <li key={`${event.scan.id}-${index}`}>
                  <span className="text-ink">
                    {timeUtc(event.scan.createdAt)} / {event.scan.verdict} · {channelLabel[event.scan.channel]}
                  </span>
                  <br />
                  <span className="break-all">{event.scan.indicatorChecked}</span>
                </li>
              ) : (
                <li key={`${event.ruleId}-${index}`}>
                  <span className="text-ink">
                    {timeUtc(event.at)} / {event.type === "BLOCK" ? "Blocked" : "Bypassed"} · {event.indicator_type}
                  </span>
                  <br />
                  <span className="break-all">{event.value}</span>
                </li>
              ),
            )}
          </ul>
        )}
      </PopoverContent>
    </Popover>
  );
}

function AccountMenu() {
  const router = useRouter();
  const { session } = useLive();
  const initials = session
    ? session.displayName
        .split(/\s+/)
        .map((part) => part[0])
        .join("")
        .slice(0, 2)
        .toUpperCase()
    : "";

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="ghost" size="sm" className="gap-1.5 px-2 font-semibold" aria-label="Account">
          {initials || "Account"}
          <ChevronDownIcon className="size-3 text-subtle" />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-60">
        {session ? (
          <DropdownMenuLabel className="text-[12px] leading-[1.5] font-normal">
            <span className="block font-semibold text-ink">{session.displayName}</span>
            <span className="block text-subtle">{session.email}</span>
          </DropdownMenuLabel>
        ) : null}
        <DropdownMenuSeparator />
        <DropdownMenuItem onSelect={() => router.push("/settings")}>Settings</DropdownMenuItem>
        <DropdownMenuItem
          onSelect={async () => {
            await api.logout().catch(() => undefined);
            router.replace("/login");
          }}
        >
          Sign out
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}

