import type { Metadata } from "next";
import { ThreatsView } from "./threats-view";

export const metadata: Metadata = { title: "Threat Monitoring" };

export default function ThreatsPage() {
  return <ThreatsView />;
}
