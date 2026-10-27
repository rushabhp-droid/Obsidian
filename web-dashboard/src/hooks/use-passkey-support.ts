"use client";

import { useSyncExternalStore } from "react";
import { passkeysSupported } from "@/lib/webauthn";

const noSubscribe = () => () => {};

/** WebAuthn availability. The server snapshot assumes support so hydration matches. */
export function usePasskeySupport() {
  return useSyncExternalStore(noSubscribe, passkeysSupported, () => true);
}
