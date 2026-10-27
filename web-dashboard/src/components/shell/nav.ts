// Only implemented pages appear here. Audit Log is tracked as gated P1 work;
// Code Guard is implemented and remains available to admins.

export interface NavItem {
  href: string;
  label: string;
}

export const overviewItem: NavItem = { href: "/dashboard", label: "Overview" };

export const navGroups: { label: string; items: NavItem[] }[] = [
  {
    label: "PROTECTION",
    items: [
      { href: "/threats", label: "Threat Monitoring" },
      { href: "/emails", label: "Email Security" },
      { href: "/agents", label: "Endpoint Security" },
      { href: "/code", label: "Code Guard" },
    ],
  },
  {
    label: "MANAGEMENT",
    items: [
      { href: "/firewall", label: "Rules and Policies" },
      { href: "/consumer-reports", label: "Consumer Reports" },
      { href: "/settings", label: "Settings" },
    ],
  },
];

export function sectionLabel(pathname: string) {
  const items = [overviewItem, ...navGroups.flatMap((g) => g.items)];
  return items.find((item) => pathname === item.href || pathname.startsWith(`${item.href}/`))?.label ?? "";
}
