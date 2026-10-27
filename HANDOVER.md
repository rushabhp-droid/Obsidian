# Handover

Updated 2026-10-28. This is a short work-resume note, not a specification. For routine tasks, use the relevant source documents directly.

Last code verification: backend and daemon tests passed at `61f36fd` on 2026-10-27; later commits were documentation-only.

## Where to start

- [`README.md`](README.md): implemented status, setup, and run instructions.
- [`TODO.md`](TODO.md): active backlog, scope gates, and acceptance checks.
- [`docs/protocol.md`](docs/protocol.md): API and WebSocket message contracts.
- [`db/init.sql`](db/init.sql) and [`.env.example`](.env.example): schema and configuration keys.
- [`AGENTS.md`](AGENTS.md) and the nearest component-level `AGENTS.md`: shared and local engineering rules.

## Current pickup

1. Complete the real-host firewall milestone in the P0 backlog.
2. Resolve Android compile and backend-contract gaps, then verify the phone report path.
3. Finish demo environment setup if a public demo is planned.

The previous detailed status and design snapshot is retained at [`docs/archive/handover-2026-10-27.md`](docs/archive/handover-2026-10-27.md). Treat it as historical context; update this file only when handing off active work.
