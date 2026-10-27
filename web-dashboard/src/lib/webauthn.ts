// Passkeys through Spring Security's built-in WebAuthn endpoints (TODO.md §3
// lib/webauthn.ts). Mirrors the JSON contract of Spring's own webauthn script:
// options come back base64url-encoded and credentials go back the same way.

import { API_MODE, csrfHeaders } from "./api";

function toBase64Url(buffer: ArrayBuffer) {
  const bytes = new Uint8Array(buffer);
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function fromBase64Url(value: string): ArrayBuffer {
  const base64 = value.replace(/-/g, "+").replace(/_/g, "/").padEnd(Math.ceil(value.length / 4) * 4, "=");
  const binary = atob(base64);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
  return bytes.buffer;
}

async function postJson<T>(path: string, body: unknown, extraHeaders: Record<string, string> = {}): Promise<T> {
  const response = await fetch(path, {
    method: "POST",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json", Accept: "application/json", ...csrfHeaders(), ...extraHeaders },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (!response.ok) throw new Error(`${path} returned ${response.status}`);
  const text = await response.text();
  return (text ? JSON.parse(text) : {}) as T;
}

type CredentialDescriptorJson = { id: string; type: "public-key"; transports?: AuthenticatorTransport[] };

export function passkeysSupported() {
  return typeof window !== "undefined" && "PublicKeyCredential" in window;
}

/** Sign in with a discoverable passkey; Spring sets the __Host-session cookie on success. */
export async function signInWithPasskey(turnstileToken: string | null) {
  if (API_MODE === "mock") return;
  const turnstile: Record<string, string> = turnstileToken ? { "CF-Turnstile-Response": turnstileToken } : {};
  const options = await postJson<
    Omit<PublicKeyCredentialRequestOptions, "challenge" | "allowCredentials"> & {
      challenge: string;
      allowCredentials?: CredentialDescriptorJson[];
    }
  >("/webauthn/authenticate/options", undefined, turnstile);

  const credential = (await navigator.credentials.get({
    publicKey: {
      ...options,
      challenge: fromBase64Url(options.challenge),
      allowCredentials: options.allowCredentials?.map((c) => ({ ...c, id: fromBase64Url(c.id) })),
    },
  })) as PublicKeyCredential | null;
  if (!credential) throw new Error("No passkey was selected.");

  const response = credential.response as AuthenticatorAssertionResponse;
  const result = await postJson<{ authenticated?: boolean }>(
    "/login/webauthn",
    {
      id: credential.id,
      rawId: toBase64Url(credential.rawId),
      response: {
        authenticatorData: toBase64Url(response.authenticatorData),
        clientDataJSON: toBase64Url(response.clientDataJSON),
        signature: toBase64Url(response.signature),
        userHandle: response.userHandle ? toBase64Url(response.userHandle) : undefined,
      },
      credType: credential.type,
      clientExtensionResults: credential.getClientExtensionResults(),
      authenticatorAttachment: credential.authenticatorAttachment,
    },
    turnstile,
  );
  if (result.authenticated === false) throw new Error("The passkey was not accepted.");
}

/** Enroll a passkey for the signed-in user. */
export async function registerPasskey(label: string) {
  if (API_MODE === "mock") return;
  const options = await postJson<
    Omit<PublicKeyCredentialCreationOptions, "challenge" | "user" | "excludeCredentials"> & {
      challenge: string;
      user: { id: string; name: string; displayName: string };
      excludeCredentials?: CredentialDescriptorJson[];
    }
  >("/webauthn/register/options", undefined);

  const credential = (await navigator.credentials.create({
    publicKey: {
      ...options,
      challenge: fromBase64Url(options.challenge),
      user: { ...options.user, id: fromBase64Url(options.user.id) },
      excludeCredentials: options.excludeCredentials?.map((c) => ({ ...c, id: fromBase64Url(c.id) })),
    },
  })) as PublicKeyCredential | null;
  if (!credential) throw new Error("Passkey creation was cancelled.");

  const response = credential.response as AuthenticatorAttestationResponse;
  const result = await postJson<{ success?: boolean }>("/webauthn/register", {
    publicKey: {
      credential: {
        id: credential.id,
        rawId: toBase64Url(credential.rawId),
        response: {
          attestationObject: toBase64Url(response.attestationObject),
          clientDataJSON: toBase64Url(response.clientDataJSON),
          transports: response.getTransports?.() ?? [],
        },
        type: credential.type,
        clientExtensionResults: credential.getClientExtensionResults(),
        authenticatorAttachment: credential.authenticatorAttachment,
      },
      label,
    },
  });
  if (result.success === false) throw new Error("The server did not save the passkey.");
}
