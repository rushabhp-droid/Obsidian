"use client";

const TAB_ID_KEY = "obsidian.dashboard.tab-id";
const TAB_RECORD_PREFIX = "obsidian.dashboard.tab.";
const LAST_CLOSED_TAB_KEY = "obsidian.dashboard.last-closed-tab";
const MOCK_SIGNED_OUT_KEY = "obsidian.mock.signed-out";
const HEARTBEAT_MS = 15_000;
const TAB_STALE_MS = 5 * 60_000;

interface TabRecord {
  state: "active" | "closing";
  updatedAt: number;
}

function tabId(): string | null {
  try {
    const current = window.sessionStorage.getItem(TAB_ID_KEY);
    if (current) return current;
    const created = window.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(36).slice(2)}`;
    window.sessionStorage.setItem(TAB_ID_KEY, created);
    return created;
  } catch {
    return null;
  }
}

function readRecord(key: string): TabRecord | null {
  try {
    const raw = window.localStorage.getItem(key);
    if (!raw) return null;
    const record = JSON.parse(raw) as Partial<TabRecord>;
    if ((record.state !== "active" && record.state !== "closing") || typeof record.updatedAt !== "number") return null;
    return record as TabRecord;
  } catch {
    return null;
  }
}

function hasActiveOtherTab(currentId: string): boolean {
  try {
    const now = Date.now();
    for (let i = 0; i < window.localStorage.length; i++) {
      const key = window.localStorage.key(i);
      if (!key?.startsWith(TAB_RECORD_PREFIX) || key === `${TAB_RECORD_PREFIX}${currentId}`) continue;
      const record = readRecord(key);
      if (record?.state === "active" && now - record.updatedAt < TAB_STALE_MS) return true;
    }
    return false;
  } catch {
    // If tab storage is unavailable, don't risk signing out another open tab.
    return true;
  }
}

function writeRecord(id: string, state: TabRecord["state"]) {
  try {
    window.localStorage.setItem(`${TAB_RECORD_PREFIX}${id}`, JSON.stringify({ state, updatedAt: Date.now() }));
  } catch {
    // Storage may be disabled; live mode still has its server-side session timeout.
  }
}

function markTabClosing(id: string) {
  writeRecord(id, "closing");
  if (!hasActiveOtherTab(id)) {
    try {
      window.localStorage.setItem(LAST_CLOSED_TAB_KEY, id);
    } catch {
      // Storage may be disabled; the live backend still expires disconnected sessions.
    }
  }
}

export function markDashboardLogin() {
  try {
    window.localStorage.removeItem(LAST_CLOSED_TAB_KEY);
    window.localStorage.removeItem(MOCK_SIGNED_OUT_KEY);
  } catch {
    // The login itself remains authoritative when browser storage is unavailable.
  }
}

export function markMockSignedOut() {
  try {
    window.localStorage.setItem(MOCK_SIGNED_OUT_KEY, "true");
  } catch {
    // Mock mode has no server session to revoke.
  }
}

export function clearMockSignedOut() {
  try {
    window.localStorage.removeItem(MOCK_SIGNED_OUT_KEY);
  } catch {
    // The login itself remains authoritative when browser storage is unavailable.
  }
}

/** Register this tab and detect a new tab opened after the previous last tab closed. */
export function registerDashboardTab(): { needsLogin: boolean; dispose: () => void } {
  if (typeof window === "undefined") return { needsLogin: false, dispose: () => undefined };
  const id = tabId();
  if (!id) return { needsLogin: false, dispose: () => undefined };

  try {
    const lastClosedId = window.localStorage.getItem(LAST_CLOSED_TAB_KEY);
    const explicitlySignedOut = window.localStorage.getItem(MOCK_SIGNED_OUT_KEY) === "true";
    if (explicitlySignedOut || (lastClosedId && lastClosedId !== id)) {
      return { needsLogin: true, dispose: () => undefined };
    }

    // sessionStorage survives a reload in the same tab. A matching close marker
    // therefore means this is a reload/reconnect, not a newly opened tab.
    if (lastClosedId === id) window.localStorage.removeItem(LAST_CLOSED_TAB_KEY);
  } catch {
    return { needsLogin: false, dispose: () => undefined };
  }

  writeRecord(id, "active");
  const heartbeat = window.setInterval(() => writeRecord(id, "active"), HEARTBEAT_MS);
  const close = () => markTabClosing(id);
  window.addEventListener("pagehide", close);

  return {
    needsLogin: false,
    dispose: () => {
      window.clearInterval(heartbeat);
      window.removeEventListener("pagehide", close);
      markTabClosing(id);
    },
  };
}
