import type { Metadata } from "next";
import { Suspense } from "react";
import { RulesView } from "./rules-view";

export const metadata: Metadata = { title: "Rules and Policies" };

export default function FirewallPage() {
  return (
    <Suspense>
      <RulesView />
    </Suspense>
  );
}
