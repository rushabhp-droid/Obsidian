"use client";

import { useCallback, useEffect, useState } from "react";

interface ResourceState<T> {
  data: T | undefined;
  error: Error | undefined;
  loading: boolean;
}

/**
 * Loads data on mount (client only, so server and client render the same
 * loading state). `load` must be stable: a module-level function or useCallback.
 * A failed reload keeps the previous data so the page never swaps real results
 * for an empty or "success" state.
 */
export function useResource<T>(load: () => Promise<T>, enabled = true) {
  const [state, setState] = useState<ResourceState<T>>({ data: undefined, error: undefined, loading: true });
  const [version, setVersion] = useState(0);

  useEffect(() => {
    if (!enabled) return;
    let cancelled = false;
    setState((s) => ({ ...s, loading: true }));
    load()
      .then((data) => !cancelled && setState({ data, error: undefined, loading: false }))
      .catch((error: Error) => !cancelled && setState((s) => ({ ...s, error, loading: false })));
    return () => {
      cancelled = true;
    };
  }, [load, version, enabled]);

  const reload = useCallback(() => setVersion((v) => v + 1), []);
  const mutate = useCallback((update: (data: T | undefined) => T) => {
    setState((s) => ({ ...s, data: update(s.data) }));
  }, []);

  return { ...state, reload, mutate };
}
