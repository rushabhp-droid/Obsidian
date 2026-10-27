import { cn } from "cn"

// Figma "Loading placeholder": 10 px bars, 3 px radius, border color.
function Skeleton({ className, ...props }: React.ComponentProps<"div">) {
  return (
    <div
      data-slot="skeleton"
      className={cn("h-[10px] animate-pulse rounded-[3px] bg-line", className)}
      {...props}
    />
  )
}

export { Skeleton }
