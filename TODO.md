# Active backlog

Last reviewed: 2026-10-28.

This file tracks unfinished work only. The current implementation status is in [`README.md`](README.md). The original hackathon plan is preserved in [`docs/archive/hackathon-plan.md`](docs/archive/hackathon-plan.md) for historical context.

## Scope gates

- **P0:** required for the end-to-end demo.
- **P1:** start only after the hour-8 firewall milestone below passes.
- **ROADMAP:** do not implement, including stubs.
- Code Guard was built early at the user's request. It is an existing P1 exception; other P1 work remains gated.

## P0 — open work

Work through these in order. The hour-8 firewall milestone is sections 1 and 3 together; P1 opens only when both pass.

### 1. Verify web-to-daemon enforcement on a Linux host

The daemon has passed end-to-end checks in `--dry-run`; real iptables enforcement is unverified.

- [ ] Run `scripts/build.sh run --daemon-root`, add a domain in the web firewall editor, and confirm the daemon blocks `curl` to it.
- [ ] Bypass that rule and confirm `curl` works again.

### 2. Connect the Android app to the backend

The app builds and its unit tests pass (`cd mobile-android && ./gradlew assembleDebug testDebugUnitTest`), but it never talks to the backend for rules: nothing signs in, and report, trust and rule changes only update in-memory lists. See [`mobile-android/AGENTS.md`](mobile-android/AGENTS.md) and [`docs/protocol.md`](docs/protocol.md).

Backend contract:

- [ ] Add a sign-in screen. Call `POST /api/v1/auth/login` with `client: MOBILE` and a Turnstile token when the server has Turnstile enabled; login returns `{token, expiresAt}`, so read the profile from `/api/v1/auth/me`. Remove the `priya@example.com` default in `TokenStorage`.
- [ ] Make the API origin configurable in settings and replace the `obsidian.example.com` placeholder. `setCustomBaseUrl` is never called and has no effect once the lazy Retrofit service exists. Allow cleartext HTTP only for local development hosts (`10.0.2.2`, `localhost`) through a network security config.
- [ ] Load rules from `GET /api/v1/rules` and remove the seeded sample messages and rules in `MessageRepository` and `RuleRepository`.
- [ ] Send **Report as scam** through `POST /api/v1/rules/report` with the backend's fields (`indicatorType`, `indicatorValue`, optional `threatCategory`). Report the link host as `DOMAIN` when it should be blocked on the daemon.
- [ ] Send **I trust this link** and trusted rules through `POST /api/v1/rules/bypass` with `ttlMinutes` (1–1440, required for employees).
- [ ] Decide what **Delete** on the Rules screen does. The backend has no `DELETE /api/v1/rules/{id}`, so remove that unused call. Removing a block can map to a bypass; there is no endpoint for removing a trust rule.
- [ ] Match scan responses: allow `category: null` and consume `indicators[]` rather than `indicatorChecked`/`details`.

Invariants from `AGENTS.md`:

- [ ] When a scan request fails, show **Checking** (`PENDING`), never **No threats found**. `ThreatScanner.evaluateLocalHeuristics` returns `CLEAN` for unknown links; remove it (a local heuristic classifier is P1 work).
- [ ] Fail closed on contact lookup. Without `READ_CONTACTS`, `ContactResolver` treats every sender as unknown, so text from saved contacts is sent to the backend.
- [ ] Watch only the default SMS package. `MessagingNotificationService` also matches any package containing `sms` or `messaging`.
- [ ] Deduplicate per message, not per notification post. WhatsApp reposts the conversation history with a new `postTime`, so earlier messages are scanned and alerted again.
- [ ] Stop logging bearer tokens and message text: OkHttp `BODY` logging in `ApiClient` and the `Log.d` calls in `MessagingNotificationService`.

Device readiness:

- [ ] Request `READ_CONTACTS` and `POST_NOTIFICATIONS` at runtime. Make **Turn on message checking** open the notification-listener settings; today it only sets a preference.
- [ ] Turn off SIM change detection until P1 opens. It runs on every resume and clears the bearer token when no SIM is present, which signs out a SIM-less test device.
- [ ] Install on a device or emulator, sign in, and confirm that a link from an unknown sender is scanned by the backend and appears in the dashboard live feed.

Not blocking the demo:

- [ ] Open the message screen when a threat notification is tapped; the `OPEN_SCREEN` and `EXTRA_URL` extras are never read.
- [ ] Don't skip the lock screen when the device has no biometric or screen lock (`MainActivity`).
- [ ] Don't fall back silently to unencrypted preferences in `TokenStorage`.
- [ ] Make `RulePrecedenceTest` and `SimFingerprintTest` exercise app code; both test copies of the logic defined inside the test file.

### 3. Verify the Android report path

- [ ] Report a domain from Android and confirm the daemon from section 1 blocks it. This completes the hour-8 milestone.

### 4. Prepare the demo environment

- [ ] Configure the named Cloudflare tunnel, domain, and matching Turnstile host keys. Check: the dashboard loads at the public domain and sign-in passes Turnstile.
- [ ] Configure VirusTotal and prewarm the demo indicators. Check: each entry in `scripts/demo-indicators.txt` returns its expected detection.
- [ ] Configure Gmail OAuth in Testing mode and add the demo accounts as test users. Check: a demo account connects Gmail and a test message appears on the **Email Security** page.
- [ ] If Code Guard will be demonstrated, configure the GitHub App and install gitleaks and osv-scanner. Check: a push containing a test secret produces a failing Check Run.

## P1 — gated work

Begin only after the hour-8 milestone passes.

- [ ] TOTP MFA and QR enrollment.
- [ ] Heuristic classifier for messages without links or attachments.
- [ ] SIM change detection: review the existing code and turn it back on (it is turned off under P0).
- [ ] `BypassExpiryJob` to reapply a block when a bypass expires.
- [ ] TTL picker and audit log view in the dashboard.
- [ ] Daemon ACK latency in the dashboard.

## Backlog maintenance

When work is completed, update this list and the status in `README.md`. Keep each open item verifiable and link to the owning contract or component instructions. Put completed hackathon schedule notes in the archive, not in this active backlog.
