"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { ObsidianIdentity } from "@/components/obsidian/identity";
import { API_MODE } from "@/lib/api";
import { cn } from "@/lib/utils";
import { navFor, type NavItem } from "./nav";
import { useLive } from "./live-provider";

const roleLabel = { ADMIN: "Organization admin", EMPLOYEE: "Employee", CONSUMER: "Personal account" } as const;

export function AppSidebar() {
  const pathname = usePathname();
  const { session } = useLive();
  const nav = navFor(session?.role);

  return (
    <div className="w-[218px] shrink-0 self-stretch bg-sidebar">
      <aside className="sticky top-0 flex h-screen flex-col gap-7 overflow-y-auto px-4 pt-[25px] pb-[22px]">
        <Link href="/dashboard" aria-label="Obsidian overview" className="w-fit rounded-sm">
          <ObsidianIdentity surface="dark" />
        </Link>

        <div className="min-h-[51px] text-[12px] leading-[1.4] text-sidebar-foreground">
          {session ? (
            <>
              <p>WORKSPACE</p>
              <p className="text-white">{session.organization?.name ?? session.displayName}</p>
              <p>{roleLabel[session.role]}</p>
            </>
          ) : null}
        </div>

        <nav aria-label="Primary" className="text-[12px] text-sidebar-foreground">
          {session ? <NavLink item={nav.overview} pathname={pathname} /> : null}
          {(session ? nav.groups : []).map((group) => (
            <div key={group.label} className="mt-10">
              <p className="flex h-10 items-center">{group.label}</p>
              <ul>
                {group.items.map((item) => (
                  <li key={item.href}>
                    <NavLink item={item} pathname={pathname} />
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </nav>

        {API_MODE === "mock" ? (
          <div className="text-[11px] leading-[1.4] text-sidebar-foreground">
            <p>SAMPLE DATA MODE</p>
            <p className="mt-[15px]">Not connected to the Obsidian API.</p>
            <p>Report and Bypass change this browser only.</p>
          </div>
        ) : null}
      </aside>
    </div>
  );
}

function NavLink({ item, pathname }: { item: NavItem; pathname: string }) {
  const active = pathname === item.href || pathname.startsWith(`${item.href}/`);
  return (
    <Link
      href={item.href}
      aria-current={active ? "page" : undefined}
      className={cn(
        "relative flex h-10 items-center rounded-sm pl-[13px] transition-colors hover:text-white",
        active && "text-white before:absolute before:left-0 before:h-[15px] before:w-px before:bg-white",
      )}
    >
      {item.label}
    </Link>
  );
}
