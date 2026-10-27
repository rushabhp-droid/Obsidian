# CLAUDE.md

Obsidian is a 24-hour hackathon project: one threat-rule engine that covers email (Gmail), messaging (SMS/WhatsApp on Android) and network traffic (Rust daemon on Linux), for consumers (B2C) and organizations (B2B).

**`TODO.md` is the spec and the source of truth.** It holds the schema, API table, WS protocol, module specs and schedule. This file covers only what you need on every task. When they disagree, follow `TODO.md` and tell the user.

## Status

`web-dashboard/` exists: the P0 screens from the Obsidian Figma file, on sample data until Spring is up (`NEXT_PUBLIC_API_MODE`, see `web-dashboard/README.md`). Everything else is still the plan (`README.md`, `TODO.md`). The layout below is the target from `TODO.md` §3. Create directories as you get to them. Don't scaffold every component up front.

## Scope discipline (most important rule)

Every feature is tagged in `TODO.md` §0:
- **[P0]**: build it. The demo depends on it.
- **[P1]**: build only after the **hour 8 milestone** passes (web editor add → daemon blocks `curl`; phone report → same block; bypass → `curl` works again).
- **[ROADMAP]**: **do not build**, not even stubs or "quick" versions. Examples: Windows/macOS daemon, Android DNS/VPN, device posture, fleet management, Workspace-wide scanning, Cloudflare WAF, Gmail push.

If a request touches a [P1] or [ROADMAP] item, say which tag it has before you implement anything. Choose the simplest thing that works for a live demo over anything general.

## Layout

| Path | Stack | Owner |
| --- | --- | --- |
| `backend-spring/` | Java 21, Spring Boot 3.5+, Gradle KTS, virtual threads | Dev 1 (auth, rules, WS, Gmail) / Dev 2 (`scanner/`) |
| `daemon-rust/` | Rust 2021, `tokio` + `tokio-tungstenite`, Linux only | Dev 2 |
| `web-dashboard/` | Next.js 15 App Router, TS, Tailwind, shadcn/ui | Dev 4 |
| `mobile-android/` | Kotlin, Jetpack Compose | Dev 3 |
| `db/init.sql` | PostgreSQL 16 schema (Hibernate `ddl-auto=validate`) | Dev 1 |
| `docs/protocol.md` | WS and scan JSON contract shared by all four components | Team |
| `docker-compose.yml` | Postgres 16 + Redis 7 | |
| `cloudflared/`, `scripts/` | Named tunnel config; seed / VT prewarm / reset scripts | |

Java packages are under `com.obsidian.{config,auth,rules,scanner,email,firewall,codeguard,audit,model}`. Header and attachment analysis belongs in `scanner/`, not `email/`. `email/` only does OAuth and polling and passes raw messages to `ScanService`.

## Cross-cutting invariants

**Rules**
- Only `RuleService` writes `threat_rules`. Every mutation does three things: upsert to Postgres, write `audit_logs`, publish to Redis `channel:firewall:events`. No other code writes rules directly.
- Upserts use `INSERT ... ON CONFLICT (org_id, user_id, indicator_type, indicator_value) DO UPDATE` (the constraint is `NULLS NOT DISTINCT`). Report and Bypass change `list_type`. They don't create duplicate rows.
- Normalize indicator values to lowercase before storing or looking them up.
- Precedence lives **only** in `RulePrecedence`: org `ADMIN_POLICY` → unexpired user `WHITELIST` (only if `policy_allow_user_bypass`) → user `BLACKLIST` → org `BLACKLIST` → global → VirusTotal/heuristics.
- TTL bypass is stored **only** in `threat_rules.expires_at`. It is never stored in Redis. `BypassExpiryJob` handles expiry. Enterprise user bypasses must have `expires_at` set.

**Scanning**
- There is one endpoint, `POST /api/v1/scan/unified`, for every channel. Don't add per-channel scan endpoints.
- Lookup order: Postgres rules → Redis `vt:cache:*` (24 h TTL) → VirusTotal v3.
- VT free tier allows 4 req/min and 500/day. Keep a client-side limiter. An unknown URL (404) gets submitted, then polled. While waiting, or when quota runs out, return **`PENDING`, never `CLEAN`/`SAFE`**.
- Verdict thresholds: 0 detections = clean, 1–2 = `SUSPICIOUS`, ≥3 = `MALICIOUS`.
- SPF/DKIM/DMARC: parse **only the topmost `Authentication-Results` header from `mx.google.com`**. "Verified official" requires all three to pass **and** a domain allowlist match.

**Auth and sessions**
- Passkeys use Spring Security's built-in WebAuthn endpoints (`/webauthn/*`, `/login/webauthn`) and its own `user_entities` / `user_credentials` tables, copied from Spring's schema. Don't hand-roll these and don't add `/api/v1/auth/passkey*` endpoints.
- Passwords use `Argon2PasswordEncoder(16, 32, 1, 19456, 2)`, which needs BouncyCastle `bcprov`.
- Web session cookie: `__Host-session` with `Secure; HttpOnly; Path=/; SameSite=Lax`, **no `Domain`**, backed by Spring Session Redis.
- Mobile gets a bearer token from password login. Android has no passkeys.
- Turnstile is verified server-side on register and login.
- Disable Spring's default login page, because it collides with the Next.js `/login`.

**Routing / origin**
- One hostname, path-based routing. `/api`, `/ws`, `/webauthn`, `/login/webauthn`, `/oauth2` and `/login/oauth2` go to Spring `:8080`. Everything else goes to Next.js `:3000`. Mirror the same path list in `next.config.ts` rewrites for the localhost fallback.
- Never use a quick tunnel (`trycloudflare.com`). It breaks passkeys (RP ID) and Turnstile.

**Rust daemon** (security-critical, and judges will ask about it)
- Validate every value as `std::net::IpAddr` or a strict domain before any system call.
- Run binaries with `Command::new(...).args([...])`. **Never use a shell** or `sh -c`.
- iptables changes go only in the dedicated `OBSIDIAN` chain, which is jumped to from `INPUT` and `OUTPUT`. Cleanup is `iptables -F OBSIDIAN`.
- `/etc/hosts` lines are `0.0.0.0 <domain> # OBSIDIAN-MANAGED`, written to a temp file and then atomically renamed. Flush DNS afterwards (`resolvectl flush-caches`). Remove the managed lines on exit.
- On every (re)connect, reconcile against the server's full `SYNC`. Heartbeat every 10 s. Clean up on SIGINT/SIGTERM.
- Authenticate to `/ws/firewall` with `Authorization: Bearer <token>`. The server stores only the SHA-256 hash in `endpoint_agents.token_hash`.

**Android**
- Read messages from `MessagingStyle` `EXTRA_MESSAGES`, not from summary text. Dedupe reposts. Packages to watch: `com.whatsapp`, `com.whatsapp.w4b` and the default SMS package.
- Saved contact: send **URLs only**, never text (privacy). Unknown sender: send text and URLs. WhatsApp shows display names, so resolve them through `ContactsContract`.
- Notifications don't carry attachments, so mobile never sends file hashes.
- Call the feature "SIM change detection", never "anti-SIM-swap".

**Code Guard** ([P1], `TODO.md` Module 5; build only after the hour 8 milestone)
- Verify `X-Hub-Signature-256` with a constant-time compare before parsing any webhook body. Return 202, then scan asynchronously.
- Run gitleaks / osv-scanner through `ProcessBuilder` with an argument list and a timeout, never a shell, in a fresh temp dir that is deleted afterwards.
- Never store, log or display a detected secret's value. Store rule, `path:line` and a SHA-256 fingerprint only.
- Release assets go through the existing `VirusTotalClient` (same cache, limiter and `PENDING` rule). Don't add a second VT client.
- Never claim the bot "hides" or "secures" a leaked key. A leaked key is compromised; the product detects it and drives rotation.

**Secrets**
- All secrets come from env (`.env`, gitignored; `.env.example` lists the keys: `VT_API_KEY`, `GMAIL_CLIENT_ID/SECRET`, `APP_ENC_KEY`, Turnstile keys, `RP_ID`, `GITHUB_APP_ID`, `GITHUB_APP_PRIVATE_KEY_PATH`, `GITHUB_WEBHOOK_SECRET`). Never commit the GitHub App private key `.pem`. Never commit `client_secret.json` or cloudflared credentials.
- TOTP secrets and Gmail refresh tokens are encrypted at rest with `pgp_sym_encrypt` using the app key.

## Contracts

`docs/protocol.md` is the shared contract between Spring, Rust, Next.js and Android. If you change a message shape (`SYNC`/`BLOCK`/`BYPASS`/`HEARTBEAT`/`ACK`, or the scan request/response), update `docs/protocol.md` **and** every consumer in the same change. Use the field names from the `TODO.md` §6 API table.

## Commands

These are the planned commands. Update this section when the real build files exist.

```sh
docker compose up -d                     # Postgres + Redis (db/ mounted into initdb)
cd backend-spring && ./gradlew bootRun   # :8080
cd web-dashboard && npm run dev          # :3000
cd daemon-rust && cargo build --release && sudo ./target/release/daemon-rust
cd mobile-android && ./gradlew installDebug
```

## Demo gotchas

- `/etc/hosts` doesn't cover subdomains, and browsers using DNS-over-HTTPS ignore it. Demo with `curl`, or turn off secure DNS in the browser.
- Pre-warm `vt:cache:*` with every demo URL and hash (`scripts/prewarm-vt.sh`). Check that each demo URL actually gets ≥3 VT detections.
- Gmail OAuth stays in Testing mode, and every demo account must be added as a test user (`gmail.modify` is a restricted scope).
