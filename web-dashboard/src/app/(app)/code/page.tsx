import type { Metadata } from "next";
import { CodeView } from "./code-view";

export const metadata: Metadata = { title: "Code Guard" };

export default function CodePage() {
  return <CodeView />;
}
