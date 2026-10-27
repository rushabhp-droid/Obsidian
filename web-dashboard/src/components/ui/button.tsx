import * as React from "react"
import { cva, type VariantProps } from "class-variance-authority"
import { cn } from "cn"
import { Slot } from "radix-ui"

// Figma "Controls" recipe: 36 px controls, 6 px radius, 12 px label.
// Disabled renders the "Unavailable" specimen instead of fading out.
const buttonVariants = cva(
  "group/button inline-flex shrink-0 items-center justify-center rounded-md border text-[12px] leading-[1.4] font-normal whitespace-nowrap transition-colors select-none focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-interactive disabled:pointer-events-none disabled:border-line disabled:bg-canvas disabled:text-subtle [&_svg]:pointer-events-none [&_svg]:shrink-0 [&_svg:not([class*='size-'])]:size-4",
  {
    variants: {
      variant: {
        default:
          "border-interactive bg-interactive text-white hover:border-[#214f91] hover:bg-[#214f91]",
        outline: "border-line bg-surface text-ink hover:bg-canvas aria-expanded:bg-canvas",
        secondary: "border-line bg-surface text-ink hover:bg-canvas aria-expanded:bg-canvas",
        ghost: "border-transparent text-ink hover:bg-canvas aria-expanded:bg-canvas",
        destructive:
          "border-critical bg-critical text-white hover:border-[#ad2c2c] hover:bg-[#ad2c2c]",
        link: "border-transparent text-interactive underline-offset-4 hover:underline disabled:border-transparent disabled:bg-transparent",
      },
      size: {
        default: "h-9 gap-1.5 px-[13px]",
        sm: "h-8 gap-1 px-3",
        lg: "h-10 gap-2 px-4 text-[13px]",
        icon: "size-9",
        "icon-sm": "size-8",
      },
    },
    // Links size to their text whatever size is passed.
    compoundVariants: [{ variant: "link", class: "h-auto px-0" }],
    defaultVariants: {
      variant: "default",
      size: "default",
    },
  }
)

function Button({
  className,
  variant = "default",
  size = "default",
  asChild = false,
  ...props
}: React.ComponentProps<"button"> &
  VariantProps<typeof buttonVariants> & {
    asChild?: boolean
  }) {
  const Comp = asChild ? Slot.Root : "button"

  return (
    <Comp
      data-slot="button"
      data-variant={variant}
      data-size={size}
      className={cn(buttonVariants({ variant, size, className }))}
      {...props}
    />
  )
}

export { Button, buttonVariants }
