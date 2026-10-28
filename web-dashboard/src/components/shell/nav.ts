import type { Role } from "@/lib/types";

// Each role gets its own workspace. Admins see the organization; employees see
// their own scans, email and rules (the API scopes the data, the menu follows it).

export interface NavItem {
  href: string;
  label: string;
}

export interface Navigation {
  overview: NavItem;
  groups: { label: string; items: NavItem[] }[];
}

const adminNav: Navigation = {
  overview: { href: "/dashboard", label: "Overview" },
  groups: [
    {
      label: "PROTECTION",
      items: [
        { href: "/threats", label: "Threat Monitoring" },
        { href: "/emails", label: "Email Security" },
        { href: "/messaging", label: "Messaging Security" },
        { href: "/agents", label: "Endpoint Security" },
        { href: "/code", label: "Code Security" },
      ],
    },
    {
      label: "MANAGEMENT",
      items: [
        { href: "/firewall", label: "Rules and Policies" },
        { href: "/consumer-reports", label: "Consumer Reports" },
        { href: "/reports", label: "Reports" },
        { href: "/audit", label: "Audit Log" },
        { href: "/settings", label: "Settings" },
      ],
    },
  ],
};

const employeeNav: Navigation = {
  overview: { href: "/dashboard", label: "My Overview" },
  groups: [
    {
      label: "MY SECURITY",
      items: [
        { href: "/threats", label: "My Threat Events" },
        { href: "/emails", label: "My Email" },
        { href: "/messaging", label: "My Messages" },
      ],
    },
    {
      label: "MANAGEMENT",
      items: [
        { href: "/firewall", label: "Rules and Policies" },
        { href: "/reports", label: "My Reports" },
        { href: "/settings", label: "Settings" },
      ],
    },
  ],
};

export function navFor(role: Role | undefined): Navigation {
  return role === "ADMIN" ? adminNav : employeeNav;
}

/** Pages only organization admins can open; employees get a notice instead. */
export const ADMIN_ONLY = ["/agents", "/code", "/consumer-reports", "/audit", "/settings/organization", "/settings/users"];

export function sectionLabel(pathname: string, role: Role | undefined) {
  const nav = navFor(role);
  const items = [nav.overview, ...nav.groups.flatMap((g) => g.items)];
  const match = items
    .filter((item) => pathname === item.href || pathname.startsWith(`${item.href}/`))
    .sort((a, b) => b.href.length - a.href.length)[0];
  return match?.label ?? (pathname.startsWith("/audit") ? "Audit Log" : "");
}
