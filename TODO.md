# Active backlog

Last reviewed: 2026-10-28.

This file tracks unfinished work only. The current implementation status is in [`README.md`](README.md). The original hackathon plan is preserved in [`docs/archive/hackathon-plan.md`](docs/archive/hackathon-plan.md) for historical context.

## Scope gates

- **P0:** required for the end-to-end demo.
- **P1:** start only after the hour-8 firewall milestone below passes.
- **ROADMAP:** do not implement, including stubs.
- Code Guard was built early at the user's request. It is an existing P1 exception; other P1 work remains gated.
- These gates are for the B2B service (web dashboard, daemon, `/api/v1`). The Android app is the separate B2C service (`/api/b2c/v1`, decided 2026-10-28): its on-device firewall, link checking and notification auditor are in scope, including the DNS/VPN mode the hackathon plan listed as ROADMAP. B2C still uses the shared backend, so the cross-cutting invariants in `AGENTS.md` apply to it.

## P0 — open work

Work through these in order. The hour-8 firewall milestone is sections 1 and 3 together; P1 opens only when both pass.

### 1. Verify web-to-daemon enforcement on a Linux host

The daemon has passed end-to-end checks in `--dry-run`; real iptables enforcement is unverified.

- [ ] Run `scripts/build.sh run --daemon-root`, add a domain in the web firewall editor, and confirm the daemon blocks `curl` to it.
- [ ] Bypass that rule and confirm `curl` works again.

### 2. Android (B2C) app: verify on a real phone

The Android app is the B2C service: it has its own API (`/api/b2c/v1`, see [`docs/protocol.md`](docs/protocol.md#b2c-app-api-apib2cv1)), consumer accounts, and on-device protection. The B2B scope gates below don't apply to it (see "Scope gates"). Built and checked on the Android 17 emulator against `gradle bootTestRun`: sign-up with an emailed code, PIN lock, the protection checklist and its toast, the URL analyzer against VirusTotal, link checking as the default browser, the notification auditor on a real SMS, the firewall blocking a newly blocked name at once, reports, themes and relocking.

- [ ] Google sign-in: create an OAuth client of type **Android** (package `com.obsidian.mobile` and the debug keystore's SHA-1) in the same Google Cloud project as `GMAIL_CLIENT_ID`, then sign in on a device or an emulator image with Google Play. Not tried yet.
- [ ] Email codes over real SMTP: fill in `SMTP_*` in `.env` and sign up with a real address. Locally the codes were only logged.
- [ ] Fingerprint unlock on hardware with an enrolled fingerprint (the emulator run used the PIN-only path).
- [ ] Install on a physical phone and run for a day with the firewall on: battery, reconnects after network changes, and "Always-on VPN".
- [ ] Notification auditor with WhatsApp (only the default SMS app was tried).
- [ ] A consumer report promoted to a global block reaches a running daemon (section 3).
- [ ] Rate-limit `/api/b2c/v1/auth/*` per IP and per email. Unlike dashboard sign-in there is no Turnstile, so password guessing and sign-up email floods are only slowed by Argon2 and the 45 s resend cooldown.

Known limits, by design or Android's: the firewall only sees DNS, so incoming connections, DNS-over-TLS (Private DNS in "hostname" mode) and browsers' own secure DNS bypass it (the app warns about Private DNS); IPv6 addresses are only blocked in link checks; while the firewall applies a rule change there is a sub-second window where lookups aren't filtered.

### 3. Verify the report path to the daemon

- [ ] Report a domain from the Android app that VirusTotal flags (or report it from 3 verified accounts, or approve it under **Consumer Reports** in the dashboard), and confirm the daemon from section 1 blocks it with real iptables. This completes the hour-8 milestone. Checked on 2026-10-28 with the daemon in `--dry-run`: the third report and an admin approval each reached it live (`BLOCK DOMAIN … applied in 2 ms`).

### 4. Prepare the demo environment

- [ ] Configure the named Cloudflare tunnel, domain, and matching Turnstile host keys. Check: the dashboard loads at the public domain and sign-in passes Turnstile.
- [ ] Configure VirusTotal and prewarm the demo indicators. Check: each entry in `scripts/demo-indicators.txt` returns its expected detection.
- [ ] Configure Gmail OAuth in Testing mode and add the demo accounts as test users. Check: a demo account connects Gmail and a test message appears on the **Email Security** page.
- [ ] If Code Guard will be demonstrated, configure the GitHub App and install gitleaks and osv-scanner. Check: a push containing a test secret produces a failing Check Run.

## P1 — gated work

Begin only after the hour-8 milestone passes.

- [ ] TOTP MFA and QR enrollment.
- [ ] Heuristic classifier for messages without links or attachments.
- [ ] SIM change detection: review the existing code and turn it back on (it is not wired into the app). `SimFingerprintTest` tests a copy of the logic inside the test file; make it exercise `SimChangeMonitor`.
- [ ] `BypassExpiryJob` to reapply a block when a bypass expires.
- [ ] TTL picker and audit log view in the dashboard.
- [ ] Daemon ACK latency in the dashboard.

## Backlog maintenance

When work is completed, update this list and the status in `README.md`. Keep each open item verifiable and link to the owning contract or component instructions. Put completed hackathon schedule notes in the archive, not in this active backlog.
