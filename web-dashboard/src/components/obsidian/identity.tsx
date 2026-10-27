import Image from "next/image";
import { cn } from "@/lib/utils";

/** Three-shard mark + wordmark. Same geometry on dark (sidebar) and light (auth) surfaces. */
export function ObsidianIdentity({ surface, className }: { surface: "dark" | "light"; className?: string }) {
  return (
    <div className={cn("flex items-center gap-2.5", className)}>
      <ObsidianMark surface={surface} size={30} />
      <span className={cn("text-[21px] leading-[1.4]", surface === "dark" ? "text-white" : "text-ink")}>obsidian</span>
    </div>
  );
}

export function ObsidianMark({ surface, size }: { surface: "dark" | "light"; size: number }) {
  return (
    <Image
      src={surface === "dark" ? "/brand/mark-on-dark.svg" : "/brand/mark-on-light.svg"}
      alt=""
      width={size}
      height={size}
      unoptimized
      priority
      className="shrink-0"
    />
  );
}
