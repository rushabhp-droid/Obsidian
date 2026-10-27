import { cn } from "@/lib/utils";
import { toneText, type Tone } from "@/lib/format";

/** White 8 px panel with a 1 px border; the base container on every console page. */
export function Panel({ className, ...props }: React.ComponentProps<"section">) {
  return (
    <section
      className={cn("flex min-w-0 flex-col gap-4 rounded-lg border border-line bg-surface p-5", className)}
      {...props}
    />
  );
}

/** 15 px semibold title with an optional "  /  context" suffix. */
export function PanelTitle({ children, meta, className }: { children: React.ReactNode; meta?: React.ReactNode; className?: string }) {
  return (
    <h2 className={cn("text-[15px] leading-normal font-semibold text-ink", className)}>
      {children}
      {meta ? <span className="text-[11px] font-normal text-subtle">{"   /   "}{meta}</span> : null}
    </h2>
  );
}

export function PageHeading({
  title,
  subtitle,
  actions,
}: {
  title: string;
  subtitle?: React.ReactNode;
  actions?: React.ReactNode;
}) {
  return (
    <div className="flex items-center gap-4">
      <div className="min-w-0 flex-1 leading-[1.5]">
        <h1 className="text-[28px] text-ink">{title}</h1>
        {subtitle ? <p className="text-[13px] text-subtle">{subtitle}</p> : null}
      </div>
      {actions ? <div className="flex shrink-0 items-center gap-2.5">{actions}</div> : null}
    </div>
  );
}

export function MetricCard({
  label,
  value,
  caption,
  tone = "neutral",
}: {
  label: string;
  value: React.ReactNode;
  caption: React.ReactNode;
  tone?: Tone;
}) {
  return (
    <div className="min-w-0 flex-1 rounded-lg border border-line bg-surface p-[18px] leading-[1.65] text-subtle">
      <p className="text-[12px]">{label}</p>
      <p className={cn("text-[30px] font-semibold", tone === "neutral" ? "text-ink" : toneText[tone])}>{value}</p>
      <p className="truncate text-[11px]">{caption}</p>
    </div>
  );
}

const noticeTone = {
  info: "bg-info-soft",
  critical: "bg-critical-soft",
  warning: "bg-warning-soft",
} as const;

/** Figma "Context notice": a tinted 6 px block with a semibold lead line. */
export function ContextNotice({
  tone = "info",
  title,
  children,
  action,
  className,
}: {
  tone?: keyof typeof noticeTone;
  title: React.ReactNode;
  children?: React.ReactNode;
  action?: React.ReactNode;
  className?: string;
}) {
  return (
    <div
      role={tone === "critical" ? "alert" : "status"}
      className={cn("flex w-full items-start gap-4 rounded-md p-4 text-[12px] leading-[1.6] text-ink", noticeTone[tone], className)}
    >
      <div className="min-w-0 flex-1">
        <p className="font-semibold">{title}</p>
        {children ? <div className="text-subtle">{children}</div> : null}
      </div>
      {action}
    </div>
  );
}

/** "Label   /   Value" lines used for operational facts. */
export function MetaList({ items, className }: { items: [string, React.ReactNode][]; className?: string }) {
  return (
    <dl className={cn("text-[12px] leading-[2.15] text-subtle", className)}>
      {items.map(([label, value]) => (
        <div key={label} className="flex gap-x-[0.6em]">
          <dt className="shrink-0">{label}</dt>
          <span aria-hidden>/</span>
          <dd className="min-w-0 flex-1">{value}</dd>
        </div>
      ))}
    </dl>
  );
}

export function PageFootnote({ children }: { children: React.ReactNode }) {
  return <p className="text-[11px] leading-[1.4] text-subtle">{children}</p>;
}
