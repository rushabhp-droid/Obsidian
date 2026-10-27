import * as React from "react"
import { cn } from "cn"

// Figma "Input": 39 px, 6 px radius; focus = 2 px blue border, invalid = red border.
function Input({ className, type, ...props }: React.ComponentProps<"input">) {
  return (
    <input
      type={type}
      data-slot="input"
      className={cn(
        "h-[39px] w-full min-w-0 rounded-md border border-line bg-surface px-[11px] text-[12px] leading-[1.4] text-ink transition-colors outline-none placeholder:text-subtle focus-visible:border-interactive focus-visible:ring-1 focus-visible:ring-interactive focus-visible:outline-none disabled:pointer-events-none disabled:cursor-not-allowed disabled:bg-canvas disabled:text-subtle aria-invalid:border-critical aria-invalid:focus-visible:ring-critical file:inline-flex file:h-6 file:border-0 file:bg-transparent file:text-[12px] file:text-ink",
        className
      )}
      {...props}
    />
  )
}

export { Input }
