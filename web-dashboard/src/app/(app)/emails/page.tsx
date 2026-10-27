import type { Metadata } from "next";
import { EmailsView } from "./emails-view";

export const metadata: Metadata = { title: "Email Security" };

export default function EmailsPage() {
  return <EmailsView />;
}
