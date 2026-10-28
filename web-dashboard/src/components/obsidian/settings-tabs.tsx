"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useLive } from "@/components/shell/live-provider";
import { cn } from "@/lib/utils";

const TABS = [
  { href: "/settings", label: "Integrations", admin: false },
  { href: "/settings/organization", label: "Organization", admin: true },
  { href: "/settings/users", label: "Users & roles", admin: true },
];

/** Settings sections as links, styled like the other tab rows. Employees only have Integrations. */
export function SettingsTabs() {
  const pathname = usePathname();
  const { session } = useLive();
  const tabs = TABS.filter((tab) => !tab.admin || session?.role === "ADMIN");
  if (tabs.length < 2) return null;
  return (
    <nav aria-label="Settings sections" className="flex w-full items-start gap-6 border-b border-line pt-[5px] pb-[13px]">
      {tabs.map((tab) => {
        const active = pathname === tab.href;
        return (
          <Link
            key={tab.href}
            href={tab.href}
            aria-current={active ? "page" : undefined}
            className={cn(
              "rounded-sm text-[12px] leading-[1.8] whitespace-nowrap text-subtle underline-offset-[3px] transition-colors hover:text-ink",
              active && "font-semibold text-interactive underline",
            )}
          >
            {tab.label}
          </Link>
        );
      })}
    </nav>
  );
}
