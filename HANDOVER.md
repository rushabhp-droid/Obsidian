# Handover

Updated 2026-10-28. This is a short work-resume note, not a specification. For routine tasks, use the relevant source documents directly.

Last code verification: at `23edcfa` on 2026-10-28, backend 256/256, daemon 19/19 and Android 12/12 unit tests passed, and the Android debug APK built.

## Where to start

Read [`AGENTS.md`](AGENTS.md) for the source-of-truth map and shared rules, then the nearest component-level `AGENTS.md`. Active work and its acceptance checks are in [`TODO.md`](TODO.md).

## Current pickup

Work through the P0 sections of `TODO.md` in order. The hour-8 firewall milestone is sections 1 and 3 together; P1 work stays gated until both pass.

1. Verify web block and bypass against a real daemon (`scripts/build.sh run --daemon-root`).
2. Connect the Android app to the backend. It builds, but it has no sign-in, and report, trust and rule changes stay on the phone.
3. Report a domain from Android and confirm the daemon blocks it.
4. Prepare the demo environment if a public demo is planned.

The previous detailed status and design snapshot is retained at [`docs/archive/handover-2026-10-27.md`](docs/archive/handover-2026-10-27.md). Treat it as historical context; update this file only when handing off active work.
