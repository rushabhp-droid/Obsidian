# Agent instructions

These are the shared repository rules. Read the nearest component-level `AGENTS.md` before changing files in that component. Use the source-of-truth map below and open only the context needed for the task. Archived documents are historical references, not current instructions or specifications.

## Sources of truth

- Implemented status and setup: [`README.md`](README.md)
- Active scope and backlog: [`TODO.md`](TODO.md)
- HTTP, scan, and WebSocket contracts: [`docs/protocol.md`](docs/protocol.md)
- PostgreSQL schema: [`db/init.sql`](db/init.sql)
- Environment keys and examples: [`.env.example`](.env.example)
- Actual runtime behavior: implementation and tests

`HANDOVER.md` is a short pickup note. Read it when resuming a longer task or handoff; it is not a specification.

## Scope

- Follow the P0, P1, and ROADMAP gates in `TODO.md`.
- P1 work starts only after the hour-8 end-to-end firewall milestone passes. Code Guard was built early at the user's request; this is an existing exception, not approval for other P1 work.
- ROADMAP items must not be implemented, including stubs.
- These gates cover the B2B service (dashboard, daemon, `/api/v1`). The Android app is the separate B2C service on `/api/b2c/v1`; its scope is in `TODO.md` ("Scope gates") and `mobile-android/AGENTS.md`.

## Cross-cutting invariants

- Only `RuleService` mutates `threat_rules`; mutations upsert, audit, and publish. B2C consumers' own rules live in `consumer_rules`; a consumer report reaches `threat_rules` only as a global block through `RuleService.blockGlobally`, and never for a protected domain.
- Consumer (`/api/b2c/**`, `obc_` tokens, `consumers`) and dashboard (`/api/v1`, sessions and `obm_` tokens, `users`) accounts stay separate: neither credential works on the other API. Normalize indicators before storing or looking them up. Rule precedence is implemented in `RulePrecedence`.
- Unknown or unavailable VirusTotal results remain `PENDING`, never `CLEAN`.
- Changes to API or WebSocket shapes update `docs/protocol.md` and every consumer in the same change.
- Keep secrets in environment/config files that are gitignored. Never commit `.env`, private keys, OAuth client secrets, tunnel credentials, or generated demo credentials.
- Preserve session, WebAuthn, encryption, and privacy guarantees documented in the relevant component instructions and protocol.

## Workflow

- Read the relevant status, contract, and component instructions before changing behavior.
- Run the checks relevant to the changed components before committing or pushing.
- Stage explicit paths; never use `git add -A`.
- The user's standing preference is to commit and push completed tasks to `main`; follow any newer instruction for a particular task.
