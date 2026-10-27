"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { ObsidianIdentity, ObsidianMark } from "@/components/obsidian/identity";
import { ContextNotice } from "@/components/obsidian/panel";
import { Turnstile, TURNSTILE_SITE_KEY } from "@/components/obsidian/turnstile";
import { api, API_MODE, ApiError } from "@/lib/api";
import { markDashboardLogin } from "@/lib/dashboard-tab-session";
import { signInWithPasskey } from "@/lib/webauthn";
import { usePasskeySupport } from "@/hooks/use-passkey-support";

type Failure = { title: string; detail: string; tone: "critical" | "warning" };

function describeFailure(error: unknown, method: "passkey" | "password"): Failure {
  if (error instanceof ApiError && error.status === 403) {
    return {
      tone: "warning",
      title: "You don't have access to this workspace",
      detail: "Ask an organization administrator to review your membership.",
    };
  }
  if (error instanceof ApiError && error.status === 400) {
    return { tone: "warning", title: "Bot check failed", detail: "Complete the challenge again, then sign in." };
  }
  if (error instanceof ApiError && error.status === 401) {
    return { tone: "critical", title: "Sign-in failed", detail: "The email or password is incorrect." };
  }
  if (error instanceof DOMException && error.name === "NotAllowedError") {
    return { tone: "warning", title: "Passkey cancelled", detail: "No passkey was used. Try again or use your password." };
  }
  return {
    tone: "critical",
    title: "Sign-in failed",
    detail: method === "passkey" ? "The passkey could not be verified. Try again or use your password." : "Try again in a moment.",
  };
}

export function LoginView() {
  const router = useRouter();
  const params = useSearchParams();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [pending, setPending] = useState<"passkey" | "password" | null>(null);
  const [failure, setFailure] = useState<Failure | null>(null);
  const [turnstileToken, setTurnstileToken] = useState<string | null>(null);
  const [turnstileKey, setTurnstileKey] = useState(0);
  const needsTurnstile = TURNSTILE_SITE_KEY !== "" && !turnstileToken;
  const expired = params.get("reason") === "expired";
  const canPasskey = usePasskeySupport();

  const attempt = async (method: "passkey" | "password") => {
    setPending(method);
    setFailure(null);
    try {
      if (method === "passkey") await signInWithPasskey(turnstileToken);
      else await api.login(email.trim().toLowerCase(), password, turnstileToken);
      markDashboardLogin();
      router.replace("/dashboard");
    } catch (error) {
      setFailure(describeFailure(error, method));
      // Turnstile tokens are single use; remount the widget for a fresh one.
      setTurnstileToken(null);
      setTurnstileKey((k) => k + 1);
      setPending(null);
    }
  };

  return (
    <div className="flex min-h-screen flex-col">
      <header className="flex h-[88px] shrink-0 items-center justify-between px-12">
        <ObsidianIdentity surface="light" />
        {API_MODE === "mock" ? (
          <p className="text-[12px] leading-[1.4] text-subtle">Sample data · Not connected to the API</p>
        ) : null}
      </header>

      <main className="flex flex-1 items-start gap-[100px] px-[120px] py-12">
        <div className="flex w-[440px] shrink-0 flex-col gap-6">
          <div className="flex flex-col gap-2 leading-[1.4]">
            <h1 className="text-[30px] text-ink">Sign in to Obsidian</h1>
            <p className="text-[13px] text-subtle">One rule set for email, messaging and your network.</p>
          </div>

          {API_MODE === "mock" ? (
            <ContextNotice title="Sample data mode">
              Sign-in is simulated until the app runs with NEXT_PUBLIC_API_MODE=live. Either button opens the console.
            </ContextNotice>
          ) : null}

          {failure ? (
            <ContextNotice tone={failure.tone} title={failure.title}>
              {failure.detail}
            </ContextNotice>
          ) : null}

          <form
            className="flex flex-col gap-6"
            onSubmit={(event) => {
              event.preventDefault();
              void attempt("password");
            }}
          >
            <div className="flex flex-col gap-[7px]">
              <Label htmlFor="email">Work email</Label>
              <Input
                id="email"
                type="email"
                autoComplete="username webauthn"
                placeholder="name@company.com"
                value={email}
                onChange={(event) => setEmail(event.target.value)}
              />
            </div>

            <Button
              type="button"
              className="w-fit"
              disabled={pending !== null || needsTurnstile || (API_MODE === "live" && !canPasskey)}
              onClick={() => void attempt("passkey")}
            >
              {pending === "passkey" ? "Waiting for passkey…" : "Continue with passkey"}
            </Button>

            <div className="flex items-center gap-[14px]">
              <div className="h-px flex-1 bg-line" />
              <span className="text-[11px] leading-[1.4] text-subtle">or use a password</span>
              <div className="h-px flex-1 bg-line" />
            </div>

            <div className="flex flex-col gap-[7px]">
              <Label htmlFor="password">Password</Label>
              <Input
                id="password"
                type="password"
                autoComplete="current-password"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
              />
              <p className="text-[11px] leading-[1.4] text-subtle">Never share a password or recovery code.</p>
            </div>

            <Turnstile key={turnstileKey} onToken={setTurnstileToken} />

            <Button
              type="submit"
              variant="outline"
              className="w-fit"
              disabled={pending !== null || needsTurnstile || !email.trim() || !password}
            >
              {pending === "password" ? "Signing in…" : "Sign in"}
            </Button>
          </form>

          <p className="text-[11px] leading-[1.4] text-subtle">
            Access is restricted to authorized members. Passwords are hashed with Argon2id; sessions use a host-only cookie.
          </p>
        </div>

        <div className="flex min-w-0 flex-1 flex-col gap-6">
          <div className="flex h-[250px] flex-col gap-6 bg-sidebar p-[30px]">
            <ObsidianMark surface="dark" size={56} />
            <p className="text-[22px] leading-[1.4] text-white">
              A focused workspace.
              <br />
              A deliberate access boundary.
            </p>
            <p className="text-[12px] leading-[1.4] text-sidebar-foreground">
              Review threats, triage email and manage the rules every connected agent enforces.
            </p>
          </div>
          {expired ? (
            <ContextNotice tone="warning" title="Session expired">
              Your session has ended. Sign in again to continue. Unsaved changes were not applied.
            </ContextNotice>
          ) : null}
        </div>
      </main>

      <footer className="flex justify-between px-[120px] py-8 text-[11px] leading-[1.4] text-subtle">
        <p>Obsidian · Passkeys, Argon2id passwords and Cloudflare Turnstile</p>
      </footer>
    </div>
  );
}
