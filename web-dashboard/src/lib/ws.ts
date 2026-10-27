// /ws/dashboard live feed client (TODO.md §3 lib/ws.ts). The session cookie
// authenticates the upgrade. Reconnects with capped backoff; in mock mode the
// sample store plays the server's part.

import { API_MODE } from "./api";
import { subscribe as subscribeMock } from "./mock-store";
import type { DashboardEvent } from "./types";

function dashboardSocketUrl() {
  if (process.env.NEXT_PUBLIC_WS_URL) return process.env.NEXT_PUBLIC_WS_URL;
  const scheme = window.location.protocol === "https:" ? "wss:" : "ws:";
  return `${scheme}//${window.location.host}/ws/dashboard`;
}

export function subscribeDashboard(
  onEvent: (event: DashboardEvent) => void,
  onStatus?: (connected: boolean) => void,
): () => void {
  if (API_MODE === "mock") {
    onStatus?.(true);
    return subscribeMock(onEvent);
  }

  let socket: WebSocket | null = null;
  let retry: ReturnType<typeof setTimeout> | undefined;
  let attempt = 0;
  let closed = false;

  const connect = () => {
    socket = new WebSocket(dashboardSocketUrl());
    socket.onopen = () => {
      attempt = 0;
      onStatus?.(true);
    };
    socket.onmessage = (message) => {
      try {
        onEvent(JSON.parse(message.data as string) as DashboardEvent);
      } catch {
        // Ignore frames that aren't JSON events.
      }
    };
    socket.onclose = () => {
      onStatus?.(false);
      if (closed) return;
      retry = setTimeout(connect, Math.min(1_000 * 2 ** attempt++, 15_000));
    };
  };

  connect();
  return () => {
    closed = true;
    clearTimeout(retry);
    socket?.close();
  };
}
