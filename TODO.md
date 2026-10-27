# Active backlog

Last reviewed: 2026-10-28.

This file tracks unfinished work only. The current implementation status is in [`README.md`](README.md). The original hackathon plan is preserved in [`docs/archive/hackathon-plan.md`](docs/archive/hackathon-plan.md) for historical context.

## Scope gates

- **P0:** required for the end-to-end demo.
- **P1:** start only after the hour-8 firewall milestone below passes.
- **ROADMAP:** do not implement, including stubs.
- Code Guard was built early at the user's request. It is an existing P1 exception; other P1 work remains gated.

## P0 — open work

### Verify the hour-8 firewall milestone on a Linux host

- [ ] Add a domain in the web firewall editor and confirm the real daemon blocks `curl` to it.
- [ ] Bypass that rule and confirm `curl` works again.
- [ ] Report a domain from Android and confirm the same daemon blocks it.

The daemon has passed end-to-end checks in `--dry-run`; real iptables enforcement and the Android-to-daemon report path remain unverified.

### Integrate Android with the backend

- [ ] Fix the compile mismatch: `ThreatRule` uses `ThreatCategory.MALICIOUS`, but the enum currently omits it.
- [ ] Match login to the backend contract: send `client: MOBILE` and include a Turnstile token when the server has Turnstile enabled; read the profile from `/api/v1/auth/me` because login returns `{token, expiresAt}`.
- [ ] Match scan responses: allow `category: null` and consume `indicators[]` rather than `indicatorChecked`/`details`.
- [ ] Remove the unsupported `DELETE /api/v1/rules/{id}` call; removing a block uses the bypass endpoint.
- [ ] Match report fields to the backend and report the link host as `DOMAIN` when it should be blocked on the daemon.
- [ ] Stop BODY logging of bearer tokens and message text; wire `setCustomBaseUrl` into settings and replace the placeholder default host.
- [ ] Build and exercise the app on a device or emulator. `mobile-android/` has no Gradle wrapper yet.

See [`mobile-android/AGENTS.md`](mobile-android/AGENTS.md) and [`docs/protocol.md`](docs/protocol.md).

### Prepare the demo environment

- [ ] Configure the named Cloudflare tunnel, domain, and matching Turnstile host keys.
- [ ] Configure VirusTotal and prewarm the demo indicators; confirm the expected detections.
- [ ] Configure Gmail OAuth in Testing mode and add the demo accounts as test users.
- [ ] Configure the GitHub App and scanner binaries if Code Guard will be demonstrated.

## P1 — gated work

Begin only after the hour-8 milestone passes.

- [ ] TOTP MFA and QR enrollment.
- [ ] Heuristic classifier for messages without links or attachments.
- [ ] SIM change detection review and integration.
- [ ] `BypassExpiryJob` to reapply a block when a bypass expires.
- [ ] TTL picker and audit log view in the dashboard.
- [ ] Daemon ACK latency in the dashboard.

## Backlog maintenance

When work is completed, update this list and the status in `README.md`. Keep each open item verifiable and link to the owning contract or component instructions. Put completed hackathon schedule notes in the archive, not in this active backlog.
