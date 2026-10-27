"use client";

import Script from "next/script";
import { useEffect, useRef } from "react";

interface TurnstileApi {
  render: (
    element: HTMLElement,
    options: {
      sitekey: string;
      callback: (token: string) => void;
      "expired-callback": () => void;
      "error-callback": () => void;
      size?: "normal" | "flexible" | "compact";
    },
  ) => string;
  remove: (widgetId: string) => void;
}

declare global {
  interface Window {
    turnstile?: TurnstileApi;
  }
}

export const TURNSTILE_SITE_KEY = process.env.NEXT_PUBLIC_TURNSTILE_SITE_KEY ?? "";

/**
 * Cloudflare Turnstile, verified server-side on register and login. Renders only
 * when NEXT_PUBLIC_TURNSTILE_SITE_KEY is set. Tokens are single use: remount
 * (change `key`) after each attempt to get a fresh one.
 */
export function Turnstile({ onToken }: { onToken: (token: string | null) => void }) {
  const container = useRef<HTMLDivElement>(null);
  const callback = useRef(onToken);

  useEffect(() => {
    callback.current = onToken;
  }, [onToken]);

  useEffect(() => {
    if (!TURNSTILE_SITE_KEY) return;
    let widgetId: string | undefined;
    const tryRender = () => {
      if (!window.turnstile || !container.current) return false;
      widgetId = window.turnstile.render(container.current, {
        sitekey: TURNSTILE_SITE_KEY,
        size: "flexible",
        callback: (token) => callback.current(token),
        "expired-callback": () => callback.current(null),
        "error-callback": () => callback.current(null),
      });
      return true;
    };
    const timer = tryRender() ? undefined : setInterval(() => tryRender() && clearInterval(timer), 200);
    return () => {
      clearInterval(timer);
      if (widgetId) window.turnstile?.remove(widgetId);
    };
  }, []);

  if (!TURNSTILE_SITE_KEY) return null;
  return (
    <>
      <Script src="https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit" strategy="afterInteractive" />
      <div ref={container} className="min-h-[65px] w-full" />
    </>
  );
}
