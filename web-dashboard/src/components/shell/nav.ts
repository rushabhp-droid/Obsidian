// Only built pages appear here. Messaging Security, Code Security, Binary
// Analysis, Reports and Audit Log from the Figma navigation are P1 or not in
// TODO.md; add Audit Log and Code Security when their P1 pages land.

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
    ],
  },
  {
    label: "MANAGEMENT",
    items: [
      { href: "/firewall", label: "Rules and Policies" },
      { href: "/settings", label: "Settings" },
    ],
  },
];

export function sectionLabel(pathname: string) {
  const items = [overviewItem, ...navGroups.flatMap((g) => g.items)];
  return items.find((item) => pathname === item.href || pathname.startsWith(`${item.href}/`))?.label ?? "";
}
