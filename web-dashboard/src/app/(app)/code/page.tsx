import type { Metadata } from "next";
import { CodeView } from "./code-view";

export const metadata: Metadata = { title: "Code Security" };

export default function CodePage() {
  return <CodeView />;
}
