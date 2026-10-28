# Handover

Updated 2026-10-28. This is a short work-resume note, not a specification. For routine tasks, use the relevant source documents directly.

Last code verification: 2026-10-28: `scripts/build.sh test` passed (backend 270/270, daemon 19/19, dashboard type-check and lint, Android 24/24). With `scripts/build.sh run`, consumer reports promoted by 3 reporters, by VirusTotal (EICAR) and by dashboard approval reached the dry-run daemon live, and the dashboard's Consumer Reports and Rules pages were checked in a headless browser. The Android app was run end to end on the Android 17 emulator earlier the same day.

## Where to start

Read [`AGENTS.md`](AGENTS.md) for the source-of-truth map and shared rules, then the nearest component-level `AGENTS.md`. Active work and its acceptance checks are in [`TODO.md`](TODO.md).

## Current pickup

Work through the P0 sections of `TODO.md` in order. The hour-8 firewall milestone is sections 1 and 3 together; P1 work stays gated until both pass.

1. Verify web block and bypass against a real daemon (`scripts/build.sh run --daemon-root`).
2. The Android app is now the B2C service on `/api/b2c/v1` (TODO.md section 2). Open items: Google sign-in with an Android OAuth client, real SMTP, and a physical phone.
3. Get a consumer report promoted to a global block (VirusTotal, 3 reporters or dashboard approval) and confirm the daemon blocks it.
4. Prepare the demo environment if a public demo is planned.

The previous detailed status and design snapshot is retained at [`docs/archive/handover-2026-10-27.md`](docs/archive/handover-2026-10-27.md). Treat it as historical context; update this file only when handing off active work.
