import { cn } from "@/lib/utils";
import { Skeleton } from "@/components/ui/skeleton";
import { Button } from "@/components/ui/button";
import { ContextNotice } from "./panel";

/** Panel wrapper for a dense table: heading row, the table, then the record count. */
export function TablePanel({
  title,
  meta,
  toolbar,
  footer,
  children,
  className,
}: {
  title: React.ReactNode;
  meta?: React.ReactNode;
  toolbar?: React.ReactNode;
  footer?: React.ReactNode;
  children: React.ReactNode;
  className?: string;
}) {
  return (
    <section className={cn("flex min-w-0 flex-col rounded-lg border border-line bg-surface", className)}>
      <div className="flex items-start justify-between gap-4 px-[18px] py-[15px] leading-[1.4]">
        <h2 className="min-w-0 flex-1 text-[15px] font-semibold text-ink">{title}</h2>
        {meta ? <p className="shrink-0 text-[11px] text-subtle">{meta}</p> : null}
      </div>
      {toolbar ? <div className="px-4 pb-1">{toolbar}</div> : null}
      {children}
      {footer ? <div className="px-4 py-[13px] text-[11px] text-subtle">{footer}</div> : null}
    </section>
  );
}

/** Loading placeholder bars. Never render zero metrics while loading. */
export function LoadingRows({ label = "Loading records…" }: { label?: string }) {
  return (
    <div className="flex flex-col gap-4 px-4 py-5" aria-busy="true">
      <p className="text-[12px] text-subtle">{label}</p>
      <Skeleton className="w-[310px] max-w-full" />
      <Skeleton className="w-[240px] max-w-full" />
      <Skeleton className="w-[280px] max-w-full" />
    </div>
  );
}

export function EmptyState({
  title,
  description,
  onClear,
}: {
  title: string;
  description: string;
  onClear?: () => void;
}) {
  return (
    <div className="flex flex-col items-start gap-4 px-4 py-5 text-[12px] leading-[1.4]">
      <div>
        <p className="text-ink">{title}</p>
        <p className="mt-1 text-subtle">{description}</p>
      </div>
      {onClear ? (
        <Button variant="outline" onClick={onClear}>
          Clear filters
        </Button>
      ) : null}
    </div>
  );
}

/** Secondary panels whose data failed to load; the page shows one ErrorNotice with Retry. */
export function UnavailableNote({ children, className }: { children: React.ReactNode; className?: string }) {
  return <p className={cn("text-[12px] leading-[1.4] text-subtle", className)}>{children}</p>;
}

/** API failure: keep whatever was already on screen and say what failed. */
export function ErrorNotice({ error, onRetry }: { error: Error; onRetry?: () => void }) {
  return (
    <div className="px-4 py-4">
      <ContextNotice
        tone="critical"
        title="Records unavailable"
        action={
          onRetry ? (
            <Button variant="outline" size="sm" onClick={onRetry}>
              Retry
            </Button>
          ) : undefined
        }
      >
        The request failed ({error.message.slice(0, 160)}). Earlier results are not replaced with a success state.
      </ContextNotice>
    </div>
  );
}
