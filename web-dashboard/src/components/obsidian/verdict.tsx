import { Badge } from "@/components/ui/badge";
import { toneText, verdictTone } from "@/lib/format";
import type { AuthCheck, ScanVerdict } from "@/lib/types";
import { cn } from "@/lib/utils";

/** Table form: colored uppercase verdict text, as in the Figma dense tables. */
export function VerdictText({ verdict, className }: { verdict: ScanVerdict; className?: string }) {
  return <span className={cn(toneText[verdictTone[verdict]], className)}>{verdict}</span>;
}

export function VerdictBadge({ verdict }: { verdict: ScanVerdict }) {
  return <Badge variant={verdictTone[verdict]}>{verdict}</Badge>;
}

const authCheckVariant = { PASS: "verified", FAIL: "critical", NONE: "neutral" } as const;

/** SPF / DKIM / DMARC result chip, always labelled with the mechanism and result. */
export function AuthCheckBadge({ name, result }: { name: string; result: AuthCheck }) {
  return (
    <Badge variant={authCheckVariant[result]}>
      {name} {result === "NONE" ? "none" : result.toLowerCase()}
    </Badge>
  );
}
