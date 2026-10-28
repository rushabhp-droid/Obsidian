import type { Metadata } from "next";
import { ThreatsView } from "../threats/threats-view";

export const metadata: Metadata = { title: "Messaging Security" };

export default function MessagingPage() {
  return <ThreatsView variant="messaging" />;
}
