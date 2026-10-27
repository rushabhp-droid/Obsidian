# Obsidian: Unified Consumer & Enterprise Threat Defense
**24-Hour Cybersecurity Hackathon Master Implementation Plan**

---

## 0. Scope Priorities (read this first)

Everything below is tagged **[P0]**, **[P1]** or **[ROADMAP]**. When time runs short, cut from the bottom up.

* **[P0] Must have, the demo depends on it:**
  * Passkey login (web) + Argon2id password fallback
  * `/api/v1/scan/unified` → PostgreSQL rules → Redis cache → VirusTotal v3
  * Blacklist/Whitelist editor (web) with Report & Bypass
  * WebSocket → Rust daemon → `/etc/hosts` sinkhole + `iptables` (**Linux only**)
  * Android: biometric lock, WhatsApp/SMS notification scanning (URLs only), Report button
  * Gmail polling with SPF/DKIM/DMARC badge + link/attachment scanning
* **[P1] Nice to have, build only after the hour 8 milestone works:**
  * TOTP MFA
  * Heuristic/LLM classifier for messages without links
  * SIM change detection
  * Time-bound (TTL) enterprise bypass + audit log view
  * Daemon ACK latency ("applied in X ms") on the dashboard
  * Code Guard GitHub App: gitleaks + OSV-Scanner on push, release asset hashes → VirusTotal, Check Run + dashboard card (see Module 5)
* **[ROADMAP] Mention in the pitch, do not build:**
  * Windows (`netsh`) and macOS (`pfctl`) daemon support
  * Android DNS/VPN warn mode
  * Device posture checks (SIM + biometric + heartbeat gating session access)
  * Fleet management
  * Org-wide Google Workspace scanning (needs domain-wide delegation with a service account)
  * Cloudflare WAF rules
  * Code Guard extras: Semgrep malicious-code rules, Syft/Grype binary SBOM scanning, automatic key revocation via provider APIs, pre-receive push blocking (GitHub Enterprise Server / GitLab), feeding domains found in malicious code into `threat_rules`

---

## 1. Executive Summary & Tier Differentiation

Obsidian is a real-time, cross-channel cybersecurity platform protecting **Regular People (B2C)** and **Enterprises (B2B)** across **Email, SMS/WhatsApp, and Network Traffic**, secured by a hardware-grade **FIDO2/Passkey + Argon2id** authentication stack.

| Feature Domain | Consumer Tier (Regular People) | Enterprise Tier (B2B / Organizations) |
| :--- | :--- | :--- |
| **Primary Interface** | User-friendly, zero-jargon **Android Native App** + Personal Web Portal | **Centralized SOC Admin Web Dashboard** (fleet management: [ROADMAP]) |
| **Email & Messaging** | Personal Gmail & SMS/WhatsApp shield with 1-tap "Report" or "Bypass" | Per-employee Gmail scanning + **Fleet Crowd-Immunity** (1 report blocks across all employees). Org-wide Workspace scanning: [ROADMAP] |
| **Firewall Enforcement** | Personal Whitelist/Blacklist management (Android DNS/VPN warn mode: [ROADMAP]) | **Rust System Daemon** on employee machines enforcing `iptables` & `/etc/hosts` sinkholes in real time (Linux; Windows/macOS: [ROADMAP]) |
| **Bypass Policy** | Instant self-service user bypass (adds to personal Whitelist) | **Time-bound TTL Bypass** (e.g., 15-min override via `threat_rules.expires_at`) with Admin Audit Logging [P1] |
| **Device Integrity** | Biometric Fingerprint Lock (PIN fallback) + SIM Change Alert [P1] | Live daemon heartbeat on the SOC dashboard (device posture gating: [ROADMAP]) |
| **Code & Releases** | Not offered | **Code Guard** GitHub App: leaked secrets, vulnerable dependencies and malicious release binaries flagged on every push and release [P1] |

---

## 2. System Architecture & Tech Stack ("The Hybrid Winner")

### High-Level Data Flow
$$\text{Ingress (Gmail / SMS / WhatsApp / Network)} \longrightarrow \text{PostgreSQL Rules (Whitelist/Blacklist)} \longrightarrow \text{Redis VT Cache} \longrightarrow \text{VirusTotal API v3 + Heuristics} \longrightarrow \text{Redis Pub/Sub} \longrightarrow \text{Rust OS Firewall Daemon & UI}$$

### Technology Stack
* **Cloud Control Plane & Core API:** Java 21 + Spring Boot 3.5+ (Virtual Threads enabled for high-throughput I/O). Check that your Spring Security version ships the JDBC WebAuthn repositories (`JdbcPublicKeyCredentialUserEntityRepository`, `JdbcUserCredentialRepository`); otherwise use the in-memory ones for the demo.
* **B2B Endpoint Firewall Agent:** Rust (2021 Edition, `tokio` + `tokio-tungstenite` single static binary), **Linux only** for the hackathon
* **Desktop Webapp (B2C Portal & B2B SOC):** Next.js 15 (App Router, TypeScript, Tailwind CSS, `shadcn/ui`)
* **Mobile App (Native):** Android Native (Kotlin, Jetpack Compose, `BiometricPrompt`, `NotificationListenerService`, `SubscriptionManager`)
* **Database & Cache:** PostgreSQL 16 (Persistent storage & threat rules) + Redis 7 (Session engine, VT cache, WebSocket Pub/Sub)
* **Edge & Bot Defense:** Cloudflare **named** Tunnel on a domain you own (`cloudflared`) + Cloudflare Turnstile

### Networking & Origin Setup (decide in hour 0)
* **Do not use a quick tunnel** (`cloudflared tunnel --url ...`). It gets a random `trycloudflare.com` hostname on every restart, which:
  * invalidates every registered passkey (WebAuthn credentials are bound to the RP ID / domain),
  * breaks the Turnstile site key (bound to a hostname),
  * and can't have WAF rules.
* Use a **named tunnel** on a domain in your Cloudflare account, with **one hostname** and path-based ingress so the web app and API share one origin (keeps the `__Host-session` cookie simple):
  ```yaml
  ingress:
    - hostname: obsidian.<your-domain>
      path: ^/(api|ws|webauthn|login/webauthn|oauth2|login/oauth2)(/|$)
      service: http://localhost:8080
    - hostname: obsidian.<your-domain>
      service: http://localhost:3000
    - service: http_status:404
  ```
  * Spring Security's passkey endpoints (`/webauthn/*`, `/login/webauthn`) and Spring OAuth2 client paths for Gmail (`/oauth2/authorization/google`, `/login/oauth2/code/google`) live outside `/api`, so they must be routed to Spring explicitly or they 404 on Next.js.
  * Disable Spring's generated default login page so it doesn't collide with the Next.js `/login` route.
* **Fallback:** demo on `http://localhost` (Chrome treats it as a secure context, so WebAuthn and `__Host-` cookies work) with Turnstile's test site keys. Proxy the same path list (`/api`, `/ws`, `/webauthn`, `/login/webauthn`, `/oauth2`, `/login/oauth2`) to Spring via Next.js rewrites in that mode.

---

## 3. Monorepo Directory Structure

```text
obsidian/
├── README.md
├── TODO.md
├── .gitignore                        # .env, client_secret.json, cloudflared credentials, build outputs
├── .env.example                      # VT_API_KEY, GMAIL_CLIENT_ID/SECRET, APP_ENC_KEY, TURNSTILE keys, RP_ID, GITHUB_APP_ID, GITHUB_APP_PRIVATE_KEY_PATH, GITHUB_WEBHOOK_SECRET
├── docker-compose.yml                # PostgreSQL 16 + Redis 7; mounts db/ into /docker-entrypoint-initdb.d
├── db/init.sql                       # Schema incl. Spring WebAuthn tables (Hibernate ddl-auto=validate)
├── cloudflared/config.yml            # Named tunnel + path ingress (credentials JSON gitignored)
├── scripts/                          # seed.sql (demo telemetry), prewarm-vt.sh (vt:cache:*), reset-demo.sh
├── docs/protocol.md                  # Example JSON for WS (SYNC/BLOCK/BYPASS/HEARTBEAT/ACK) + scan request/response
├── backend-spring/                   # Java 21 Spring Boot Control Plane
│   ├── build.gradle.kts              # incl. org.bouncycastle:bcprov (required by Argon2PasswordEncoder)
│   └── src/main/
│       ├── resources/application.yml # Reads secrets from env; spring.jpa.hibernate.ddl-auto=validate
│       └── java/com/obsidian/
│           ├── config/               # SecurityConfig (WebAuthn, Argon2id, __Host- cookies), SessionConfig, WebSocketConfig
│           ├── auth/                 # Passkeys (Spring Security WebAuthn), Argon2id fallback, TOTP, TurnstileVerifier, BearerTokenFilter (mobile)
│           ├── rules/                # ThreatRule, RuleController (/api/v1/rules/*), RuleService (upsert → audit → publish), RulePrecedence, BypassExpiryJob
│           ├── scanner/              # ScanController (/api/v1/scan/unified), ScanService, VirusTotalClient, HeaderAuthValidator (SPF/DKIM/DMARC), AttachmentHasher, HeuristicClassifier
│           ├── email/                # GmailOAuthController, GmailPollingWorker (hands raw messages to ScanService)
│           ├── firewall/             # FirewallWebSocketHandler (/ws/firewall), DashboardWebSocketHandler (/ws/dashboard), RedisPubSubListener, AgentController
│           ├── codeguard/            # [P1] GitHubWebhookController (/api/v1/webhooks/github), WebhookSignatureVerifier, GitHubAppClient, RepoScanJob (gitleaks + osv-scanner), ReleaseScanJob (asset hashes → VirusTotalClient), CheckRunPublisher, CodeFinding
│           ├── audit/                # AuditLog, AuditService
│           └── model/                # Shared JPA entities + Spring Data repositories (User, Organization, ScanLog, EndpointAgent)
├── daemon-rust/                      # Rust B2B OS Firewall Daemon (Linux)
│   ├── Cargo.toml
│   └── src/
│       ├── main.rs                   # Reconnect loop, SIGINT/SIGTERM → cleanup
│       ├── config.rs                 # Server URL, bearer token read from file
│       ├── protocol.rs               # serde message types (mirrors docs/protocol.md)
│       ├── reconcile.rs              # SYNC: flush OBSIDIAN chain + managed hosts lines, then apply full rule set
│       ├── validate.rs               # Strict IpAddr / domain parsing before any system call
│       ├── firewall.rs               # iptables wrapper (dedicated OBSIDIAN chain)
│       └── sinkhole.rs               # /etc/hosts atomic injector (temp file + rename)
├── web-dashboard/                    # Next.js Desktop Webapp
│   ├── next.config.ts                # Rewrites to Spring for localhost fallback
│   └── src/
│       ├── app/
│       │   ├── (auth)/login/         # Passkey + Password/TOTP + Cloudflare Turnstile UI
│       │   ├── (auth)/register/      # Account creation + passkey enrollment
│       │   └── (app)/
│       │       ├── layout.tsx        # Sidebar + auth guard
│       │       ├── dashboard/        # Threat overview, live feed, agent status
│       │       ├── emails/           # Scanned email triage, Whitelist Bypass, Report to Blacklist
│       │       ├── firewall/         # Interactive Blacklist/Whitelist Editor & Bypass toggles
│       │       ├── agents/           # Register daemon, show bearer token once, heartbeat status
│       │       ├── audit/            # Audit log view [P1]
│       │       ├── code/             # Code Guard findings per repo, push and release [P1]
│       │       └── settings/         # Connect Gmail (OAuth start), TOTP enrollment [P1]
│       ├── components/ui/            # shadcn/ui components
│       └── lib/
│           ├── api.ts                # Fetch wrapper for /api/v1
│           ├── webauthn.ts           # base64url <-> ArrayBuffer for Spring's options JSON
│           └── ws.ts                 # /ws/dashboard live feed client
└── mobile-android/                   # Native Kotlin Android App
    └── app/src/main/
        ├── AndroidManifest.xml       # BIND_NOTIFICATION_LISTENER_SERVICE, READ_CONTACTS, READ_PHONE_STATE, POST_NOTIFICATIONS
        └── java/com/obsidian/mobile/
            ├── auth/                 # BiometricAuthManager (Fingerprint + PIN fallback), token storage
            ├── network/              # API client + bearer token interceptor
            ├── sim/                  # SimChangeMonitor (SubscriptionManager listener)
            ├── intercept/            # MessagingNotificationService (SMS & WhatsApp), UrlExtractor, ContactResolver
            ├── notify/               # High-priority malicious-verdict alerts
            └── ui/                   # Compose navigation, Alert overlays, Report/Bypass actions
```

**Package ownership:** `rules/` is the shared core used by every channel, and only `RuleService` writes rules (so every mutation is audited and published). `email/` is Dev 1 (Gmail OAuth + polling); header/attachment analysis lives in `scanner/` (Dev 2) so each package has one owner.

---

## 4. Database Schema (PostgreSQL) & Redis Design

### A. PostgreSQL Schema (`db/init.sql`)
Designed for fast indexed lookups and frequent concurrent updates:

```sql
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- 1. Organizations (NULL org_id = Consumer tier; Populated = Enterprise tier)
CREATE TABLE organizations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(120) NOT NULL,
    policy_allow_user_bypass BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 2. Users & Auth Credentials
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id UUID REFERENCES organizations(id) ON DELETE SET NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'CONSUMER', -- 'CONSUMER', 'EMPLOYEE', 'ADMIN'
    password_hash TEXT NULL,                      -- Argon2id hash; NULL for passkey-only accounts
    totp_secret_enc BYTEA NULL,                   -- pgp_sym_encrypt(base32 secret, app key)
    totp_enabled BOOLEAN DEFAULT FALSE,
    sim_fingerprint_hash VARCHAR(64) NULL,        -- SHA-256 of (CarrierId + MCC + MNC + Slot)
    gmail_refresh_token_enc BYTEA NULL,           -- pgp_sym_encrypt(refresh token, app key)
    created_at TIMESTAMPTZ DEFAULT NOW()
);
-- Added while building: users.display_name, users.gmail_address and
-- users.gmail_last_polled_at (dashboard SessionInfo / GmailConnection), and a
-- scanned_emails table (Email security page, Gmail dedupe). db/init.sql has them.

-- 3. WebAuthn / Passkey Credentials (FIDO2)
-- Do NOT hand-roll this table. Spring Security WebAuthn uses its own
-- `user_entities` and `user_credentials` tables. Copy the PostgreSQL schema
-- shipped with Spring Security for those tables into init.sql, and map
-- user_entities.name to users.email.

-- 4. Unified Threat Rules (Blacklist & Whitelist for Email, SMS/WhatsApp, Firewall)
-- One row per (scope, indicator): Report/Bypass flip list_type with
-- INSERT ... ON CONFLICT (...) DO UPDATE.
CREATE TABLE threat_rules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id UUID NULL REFERENCES organizations(id) ON DELETE CASCADE,
    user_id UUID NULL REFERENCES users(id) ON DELETE CASCADE,
    indicator_type VARCHAR(20) NOT NULL, -- 'EMAIL', 'PHONE', 'URL', 'DOMAIN', 'IP', 'FILE_HASH'
    indicator_value TEXT NOT NULL,       -- Normalized lowercase value
    list_type VARCHAR(12) NOT NULL,      -- 'BLACKLIST' or 'WHITELIST'
    threat_category VARCHAR(30) NOT NULL DEFAULT 'MALICIOUS', -- 'SAFE', 'SPAM', 'PHISHING', 'MALWARE'
    source VARCHAR(30) NOT NULL,         -- 'VIRUSTOTAL', 'USER_REPORT', 'USER_BYPASS', 'ADMIN_POLICY'
    confidence_score INT DEFAULT 100,
    expires_at TIMESTAMPTZ NULL,         -- Temporary bypasses (single source of truth for TTL)
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE NULLS NOT DISTINCT (org_id, user_id, indicator_type, indicator_value)
);

CREATE INDEX idx_threat_fast_lookup ON threat_rules (indicator_type, indicator_value, list_type);
CREATE INDEX idx_threat_org_sync ON threat_rules (org_id, updated_at DESC);
CREATE INDEX idx_threat_expiry ON threat_rules (expires_at) WHERE expires_at IS NOT NULL;

-- 5. Endpoint Agents (Rust daemons)
CREATE TABLE endpoint_agents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    hostname VARCHAR(255) NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE, -- SHA-256 of the agent's bearer token
    last_heartbeat_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 6. Audit & Scan Telemetry Logs
CREATE TABLE scan_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    channel VARCHAR(20) NOT NULL,        -- 'EMAIL', 'SMS', 'WHATSAPP', 'FIREWALL', 'CODE'
    sender VARCHAR(255),
    indicator_checked TEXT,
    verdict VARCHAR(20) NOT NULL,        -- 'CLEAN', 'SUSPICIOUS', 'MALICIOUS', 'BYPASSED', 'PENDING'
    vt_positives INT DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id UUID NULL REFERENCES organizations(id) ON DELETE CASCADE,
    actor_user_id UUID NULL REFERENCES users(id) ON DELETE SET NULL,
    action VARCHAR(30) NOT NULL,         -- 'REPORT', 'BYPASS', 'BYPASS_EXPIRED', 'RULE_EDIT', 'AGENT_REGISTER'
    rule_id UUID NULL REFERENCES threat_rules(id) ON DELETE SET NULL,
    details JSONB,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 7. Code Guard findings [P1]
-- Never store a secret's value. Store the detector rule, location and a
-- fingerprint (SHA-256 of the secret) so repeat leaks can be matched.
CREATE TABLE code_findings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    repo_full_name VARCHAR(255) NOT NULL,  -- 'acme/payments-api'
    trigger VARCHAR(10) NOT NULL,          -- 'PUSH', 'PR', 'RELEASE'
    git_ref VARCHAR(255) NOT NULL,         -- commit SHA or release tag
    finding_type VARCHAR(20) NOT NULL,     -- 'SECRET', 'VULN_DEPENDENCY', 'MALICIOUS_BINARY', 'VULN_BINARY'
    severity VARCHAR(10) NOT NULL,         -- 'CRITICAL', 'HIGH', 'MEDIUM', 'LOW'
    rule_id VARCHAR(120) NOT NULL,         -- gitleaks rule, OSV/CVE id, or 'VT:<detections>'
    location TEXT,                         -- path:line, package@version, or asset filename
    secret_fingerprint VARCHAR(64) NULL,   -- SHA-256 of the secret, never the secret itself
    status VARCHAR(12) NOT NULL DEFAULT 'OPEN', -- 'OPEN', 'REVOKED', 'FALSE_POSITIVE'
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_code_findings_org ON code_findings (org_id, created_at DESC);
```

### B. Rule Precedence (implement once in `RulePrecedence`, use everywhere)
When several rules match one indicator, the first match wins:
1. **Org `ADMIN_POLICY` rule** (blacklist or whitelist): admins always win.
2. **User `WHITELIST`** that hasn't expired, and only if `organizations.policy_allow_user_bypass` is true. Enterprise user bypasses must have `expires_at` set.
3. **User `BLACKLIST`**.
4. **Org `BLACKLIST`** (crowd-immunity reports from colleagues).
5. **Global rules** (`org_id` and `user_id` both NULL).
6. No rule matched → VirusTotal + heuristics verdict.

### C. Redis Key Architecture
* **Session Store:** `spring:session:sessions:<session_id>` (Backed by `__Host-session` cookie, TTL $= 3600\text{s}$).
* **VirusTotal Cache:** `vt:cache:<sha256_or_url_id>` $\longrightarrow$ JSON verdict (TTL $= 86400\text{s}$). The free tier is **4 req/min and 500 req/day**, so pre-warm this cache with every demo URL and file hash.
* **Pub/Sub Channel:** `channel:firewall:events` $\longrightarrow$ Broadcasts real-time JSON rules to all connected Rust Daemons (via `/ws/firewall`) and Next.js SOC Dashboards (via `/ws/dashboard`).
* **TTL bypasses are NOT stored in Redis.** `threat_rules.expires_at` is the single source of truth; `BypassExpiryJob` (Spring `@Scheduled`, every 30 s) deletes expired whitelist rows, writes `BYPASS_EXPIRED` to `audit_logs`, and publishes a fresh `BLOCK` to daemons.

---

## 5. Module-by-Module Implementation Specifications

### Module 1: Hardened Login & Account Creation
1. **Edge & Bot Defense (Cloudflare Turnstile) [P0]:**
   * Serve everything through the named tunnel (see *Networking & Origin Setup*). WAF rules: [ROADMAP].
   * Every registration and login request includes a `cf-turnstile-response` token verified server-side via POST to `https://challenges.cloudflare.com/turnstile/v0/siteverify`.
2. **Primary Auth — WebAuthn / Passkeys (FIDO2) [P0]:**
   * Use Spring Security's built-in WebAuthn support (`http.webAuthn(...)`). It exposes **its own endpoints**: `/webauthn/register/options`, `/webauthn/register`, `/webauthn/authenticate/options` and `/login/webauthn`. Don't build parallel `/api/v1/auth/...` passkey endpoints.
   * Set the RP ID to the tunnel hostname (or `localhost` in fallback mode).
   * Supports platform authenticators (TouchID, Windows Hello, Android Biometrics) and roaming FIDO2 hardware keys (YubiKey).
3. **Password Fallback (Argon2id) [P0] + MFA (TOTP RFC 6238) [P1]:**
   * Configured with `Argon2PasswordEncoder(16, 32, 1, 19456, 2)` ($19\text{ MiB}$ memory, $2$ iterations, $1$ parallelism). Needs BouncyCastle on the classpath.
   * When logging in via password with TOTP enabled, session state is marked `MFA_PENDING` in Redis until verified via a 6-digit TOTP code (`dev.samstevens.totp`) or FIDO2 security key assertion.
4. **Session & Client Storage (`__Host-` Cookie) [P0]:**
   * Cookie issued strictly as:
     `Set-Cookie: __Host-session=<id>; Secure; HttpOnly; Path=/; SameSite=Lax` (no `Domain` attribute, ensuring host-only binding).
5. **Mobile Auth [P0]:**
   * No passkeys on Android (that would need Digital Asset Links / `assetlinks.json` on the domain).
   * The app logs in once with email + password (+ TOTP if enabled) and receives a bearer token, kept in Android Keystore-backed storage and unlocked by `BiometricPrompt`.

---

### Module 2: Email Access & Intelligent Threat Triage
1. **Official Mail SDK Integration [P0]:**
   * Uses `google-api-services-gmail` (OAuth2 scopes: `gmail.readonly`, `gmail.modify`).
   * `gmail.modify` is a restricted scope: keep the OAuth app in **Testing** mode and add every teammate/demo account as a **test user** on the consent screen.
   * **Polling worker** (every 30–60 s) fetches unread messages from `INBOX` and `SPAM`. Gmail push needs Pub/Sub setup: [ROADMAP].
2. **Step 1 — "Leave Official Emails Alone" (Cryptographic Verification) [P0]:**
   * Parse **only the topmost `Authentication-Results` header added by `mx.google.com`**. Headers further down can be forged by the sender.
   * An email is classified as **Verified Official** if and only if:
     $$\text{SPF} = \text{pass} \quad \land \quad \text{DKIM} = \text{pass} \quad \land \quad \text{DMARC} = \text{pass}$$
     and the sender domain matches the verified root domain list (or the user's explicit Whitelist). Attacker-owned domains can pass all three checks, so the domain list is mandatory. Verified official emails skip deep content inspection.
3. **Step 2 — Spam & Threat Scanning Pipeline:**
   * **Branch A (Links or Attachments Present) [P0]:**
     * Extract all URLs (`Regex` / parser) and compute SHA-256 hashes of all MIME attachments locally in memory.
     * Resolve each indicator in this order:
       1. PostgreSQL `threat_rules`, applying *Rule Precedence*.
       2. Redis `vt:cache:*`.
       3. If uncached, query **VirusTotal API v3** (`GET /api/v3/urls/{id}`, where `id` is the unpadded base64url of the URL, and `GET /api/v3/files/{sha256}`).
     * **Unknown URLs return 404.** Submit with `POST /api/v3/urls`, poll `GET /api/v3/analyses/{id}`, and report `PENDING` (never `SAFE`) while waiting.
     * Enforce a client-side 4 req/min limiter. When the quota is exhausted, fall back to cached/DB verdicts and mark the rest `PENDING`.
     * Categorize email: $0$ detections = `SAFE`, $1\text{--}2$ detections = `SUSPICIOUS`, $\ge 3$ detections = `MALICIOUS (PHISHING / MALWARE)`.
   * **Branch B (No Links or Attachments Present) [P1]:**
     * Run text body through a fast heuristic/LLM urgency classifier checking for social engineering patterns (CEO fraud, gift card requests, urgent wire transfers, OTP harvesting) and categorize threat level directly. Until built, these emails show as `CLEAN` with no badge.
4. **Step 3 — User Whitelist Bypass & Blacklist Reporting [P0]:**
   * **Bypass Option:** User clicks *"Trust & Bypass Security Check"* $\longrightarrow$ Upserts sender email/domain into `threat_rules` with `list_type = 'WHITELIST'` and `source = 'USER_BYPASS'`.
   * **Report Option:** User clicks *"Report Missed Phishing"* on an unflagged email $\longrightarrow$ Upserts sender email, extracted domains, and file hashes into `threat_rules` with `list_type = 'BLACKLIST'` and `source = 'USER_REPORT'` (org-scoped for employees, so it blocks for the whole org), immediately updating PostgreSQL and pushing domain blocks to the Firewall.

---

### Module 3: Native Android Mobile App (Kotlin)
1. **Native Fingerprint Lock + PIN/Password Fallback [P0]:**
   * Implemented via `androidx.biometric.BiometricPrompt` on app launch and before viewing/modifying Whitelist/Blacklist settings:
     ```kotlin
     val promptInfo = BiometricPrompt.PromptInfo.Builder()
         .setTitle("Obsidian Security Lock")
         .setSubtitle("Verify your identity to access threat controls")
         .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
         .build()
     ```
2. **SIM Change Detection [P1]:**
   * **Pitch this as "SIM change detection", not "anti-SIM-swap".** In a real SIM-swap attack the victim's number moves to the attacker's SIM, so the victim's carrier/MCC/MNC hash stays the same. Be ready for judges to ask.
   * Needs `READ_PHONE_STATE`. Queries `SubscriptionManager` for active SIM metadata (`carrierId`, `mccString`, `mncString`, `simSlotIndex`, `subscriptionId`), computes `SHA-256` on startup/resume, and compares with `users.sim_fingerprint_hash`.
   * Also register `SubscriptionManager.OnSubscriptionsChangedListener` and check `TelephonyManager.getSimState()`. A SIM going absent or losing service is a stronger signal of a real swap.
   * On mismatch or SIM removal: lock sensitive actions, invalidate the session token, and raise a high-priority **"SIM Change Detected"** alert.
3. **SMS & WhatsApp Real-Time Interception [P0]:**
   * **Listener setup:**
     * Use `NotificationListenerService` filtering packages `com.whatsapp`, `com.whatsapp.w4b`, and the default SMS package (`Telephony.Sms.getDefaultSmsPackage(context)`).
     * Read individual messages from the `MessagingStyle` extras (`EXTRA_MESSAGES`), not the summary text (grouped notifications say "3 new messages").
     * Deduplicate: WhatsApp re-posts the same notification many times.
   * **URLs only.** Notifications don't include attachment contents, so files can't be hashed on mobile.
   * **Contact List Triage Logic:**
     * For a saved contact, WhatsApp shows their **display name**, not the number. Look up `ContactsContract.Contacts.DISPLAY_NAME` for names, and `ContactsContract.PhoneLookup` only when the title is a phone number.
     * **Case 1: Unknown Number (Not in Contacts):** Send full message text + any URLs to `POST /api/v1/scan/unified` with `isSavedContact: false`.
     * **Case 2: Saved Contact:** Ignore plain conversational text for privacy. Run local URL regex; **only** if a URL is present, send just the URLs to `POST /api/v1/scan/unified` with `isSavedContact: true` and no `text`.
   * **Report Action:** Every flagged or unflagged message card in the mobile app includes a **"Report Malicious Content"** button that blacklists the extracted link/sender in PostgreSQL.

---

### Module 4: Advanced Firewall & B2B Rust Endpoint Daemon
1. **Control Plane (Webapp + Spring Boot) [P0]:**
   * Interactive **Blacklist/Whitelist Editor** table supporting search, bulk import, and instant toggle between `BLOCK` (`BLACKLIST`) and `BYPASS` (`WHITELIST`).
   * Every mutation updates PostgreSQL, writes `audit_logs`, and publishes a JSON payload to Redis channel `channel:firewall:events`. All of this goes through `RuleService` in `rules/`. `FirewallWebSocketHandler` pushes it to all connected Rust Daemons in that org, and `DashboardWebSocketHandler` pushes it to open SOC dashboards.
2. **Daemon Connection & Protocol [P0]:**
   * **Auth:** each agent gets a bearer token at registration (stored hashed in `endpoint_agents.token_hash`), sent as `Authorization: Bearer <token>` on the WebSocket handshake. Reject unauthenticated connections.
   * **Resync:** on every (re)connect the server sends a `SYNC` message with the org's full effective rule set. The daemon reconciles against it so events missed while offline aren't lost.
   * Heartbeat every 10 s updates `endpoint_agents.last_heartbeat_at`.
3. **Rust Daemon Enforcement (`daemon-rust`, Linux) [P0]:**
   * Runs as root (or with `CAP_NET_ADMIN` + write access to `/etc/hosts`).
   * **Input validation first:** parse every value as `std::net::IpAddr` or a strict domain regex before touching the system. Invoke binaries with `Command::new("iptables").args([...])`, **never a shell**. Mention this to judges.
   * **Untrusted IP Blocking (Incoming & Outgoing):**
     * Create a dedicated `OBSIDIAN` chain once, jumped to from `INPUT` and `OUTPUT`. Add rules with `iptables -A OBSIDIAN -s <IP> -j DROP` and `iptables -A OBSIDIAN -d <IP> -j DROP`. Cleanup on exit is just `iptables -F OBSIDIAN`.
     * Windows `netsh` / macOS `pfctl`: [ROADMAP].
   * **Malicious Domain Blocking (DNS Sinkhole):**
     * Append `0.0.0.0 <domain> # OBSIDIAN-MANAGED` to `/etc/hosts` via **write to a temp file + atomic rename**, then flush the DNS cache (`resolvectl flush-caches` if systemd-resolved is in use). Remove all managed lines on exit.
     * **Limitations:** the hosts file does not cover subdomains, and browsers with DNS-over-HTTPS (Chrome/Firefox "secure DNS") ignore it. **Demo with `curl`, or disable secure DNS in the demo browser.**
   * **User Bypass & Blacklist Promotion:**
     * Bypass and Blacklist are driven from the **rules table** (web or mobile). The hosts sinkhole produces no log of blocked connection attempts, so there is no "blocked connections" list to act on.
     * A `BYPASS` message removes the `iptables` rule and `/etc/hosts` entry in $< 50\text{ms}$. A `BLOCK` message re-applies them immediately.
   * **[P1] ACK:** the daemon replies `{"type":"ACK","ruleId":"...","appliedMs":12}` so the dashboard can show "applied in 12 ms".

---

### Module 5: Code Guard, B2B Code & Release Scanning [P1]
Build only after the hour 8 milestone passes. If it slips, cut this module and keep it as a pitch line.

1. **GitHub App setup [P1]:**
   * One GitHub App per demo org, installed on the demo repos. Permissions: Contents (read), Checks (write), Metadata (read). Events: `push`, `pull_request`, `release`.
   * Webhook URL: `https://obsidian.<your-domain>/api/v1/webhooks/github` (already routed to Spring by the `/api` ingress rule).
   * **Verify `X-Hub-Signature-256`** (HMAC-SHA256 of the raw body with `GITHUB_WEBHOOK_SECRET`, constant-time compare) before parsing anything. Reject unsigned or mismatched requests with 401.
   * Respond 202 immediately and run scans on a virtual-thread executor; GitHub times out webhooks after 10 s.
   * Map the App installation to `organizations.id` once at install time.
2. **Push / PR scan [P1]:**
   * Shallow-clone the pushed ref into a fresh temp directory with an installation token; delete the directory when done.
   * Run `gitleaks detect --no-git --report-format json` (secrets) and `osv-scanner --format json` (vulnerable dependencies). Invoke both with `ProcessBuilder` and an argument list, **never a shell**, with a timeout.
   * Write each result to `code_findings`. For secrets, store only the rule, `path:line` and SHA-256 fingerprint. **Never store, log or display the secret value**; mask it to its first 4 characters in the UI.
   * Publish a GitHub Check Run: `failure` if any CRITICAL/HIGH finding, otherwise `success`, with file/line annotations.
   * Publish the finding on `channel:firewall:events` so it appears in the SOC dashboard live feed.
3. **Release scan [P1]:**
   * On `release.published`, download each asset (cap 100 MB per asset), compute SHA-256 and resolve it through the existing `VirusTotalClient` (same Redis cache, 4 req/min limiter, `PENDING` rule). ≥3 detections = `MALICIOUS_BINARY` / CRITICAL.
4. **Leaked secrets: what the bot does and does not do:**
   * A pushed or released key is **compromised**. Forks, clones and caches already have it. Masking it or rewriting history does not make it safe.
   * The bot detects, alerts and opens a finding that tells the owner to **rotate the key**. Status moves to `REVOKED` once an admin confirms rotation.
   * Automatic revocation through provider APIs and blocking the push before it lands (pre-receive hooks, which github.com does not offer to third-party apps) are [ROADMAP].
5. **SOC dashboard [P1]:** `code/` page listing findings per repo with severity, location, status, and actions "Mark rotated" / "False positive" (both write `audit_logs`).

---

## 6. Core API Specification

| Method & Endpoint | Consumer | Purpose & Payload Summary |
| :--- | :--- | :--- |
| `POST /api/v1/auth/register` | Web / Mobile | Creates account (Argon2id hash if a password is given) after verifying the Turnstile token. Passkey enrollment then goes through Spring's `/webauthn/register/options` + `/webauthn/register`. |
| `POST /webauthn/authenticate/options`, `POST /login/webauthn` | Web | Spring Security built-in passkey login; creates Redis session and sets the `__Host-session` cookie. |
| `POST /api/v1/auth/login` | Web / Mobile | Password login (+ TOTP step if enabled). Web gets the session cookie; mobile gets a bearer token. |
| `POST /api/v1/scan/unified` | Mobile / Mail | `{ "channel": "SMS\|WHATSAPP\|EMAIL", "sender": "...", "isSavedContact": false, "text": "...", "urls": [...], "fileHashes": [...] }` $\longrightarrow$ Returns `{ "verdict": "MALICIOUS\|SUSPICIOUS\|CLEAN\|PENDING", "category": "PHISHING", "vtDetections": 14 }`. The single scan endpoint for all channels. |
| `GET /api/v1/auth/me`, `POST /api/v1/auth/logout` | Web / Mobile | Signed-in user (`SessionInfo`); ends the session or revokes the bearer token. |
| `GET /api/v1/scans`, `GET /api/v1/emails` | Web | Recent scan verdicts; scanned Gmail messages with SPF/DKIM/DMARC, links, attachments. |
| `GET /api/v1/integrations/gmail`, `POST …/gmail/disconnect`, `GET /api/v1/agents` | Web | Gmail connection status; agents with heartbeat. Full shapes in `docs/protocol.md`. |
| `GET /api/v1/rules` | Web / Mobile | Lists active `BLACKLIST` and `WHITELIST` entries for the user/organization. |
| `POST /api/v1/rules/report` | Web / Mobile | Upserts an unflagged email, sender, URL, or IP into `BLACKLIST` & broadcasts to Firewall. |
| `POST /api/v1/rules/bypass` | Web / Mobile | Upserts an indicator into `WHITELIST` (optional `ttlMinutes`, required for enterprise users) & removes active Firewall block. |
| `POST /api/v1/agents` | Web (Admin) | Registers a daemon for the org; returns its bearer token once. |
| `WS /ws/firewall` | Rust Daemon | Bearer-token authenticated. Server → daemon: `{"type":"SYNC","rules":[...]}` on connect, then `{"type":"BLOCK\|BYPASS","ruleId":"...","indicator_type":"IP\|DOMAIN","value":"..."}`. Daemon → server: `{"type":"HEARTBEAT"}` every 10 s, `{"type":"ACK",...}` [P1]. |
| `WS /ws/dashboard` | Web | Session-cookie authenticated. Server → browser: rule events from `channel:firewall:events`, new scan verdicts, agent heartbeat/online status, Code Guard findings. |
| `POST /api/v1/webhooks/github` | GitHub | [P1] HMAC-verified (`X-Hub-Signature-256`). Accepts `push`, `pull_request`, `release`; returns 202 and scans asynchronously. |
| `GET /api/v1/code/findings` | Web (Admin) | [P1] Lists `code_findings` for the org, filterable by repo, type, severity, status. |
| `POST /api/v1/code/findings/{id}/status` | Web (Admin) | [P1] `{ "status": "REVOKED\|FALSE_POSITIVE" }`, writes `audit_logs`. |

---

## 7. 24-Hour Hackathon Execution Schedule (4-Person Team)

* **Dev 1 (Backend Lead - Java/Spring):** Auth Stack, PostgreSQL, Redis, WebSockets, **Gmail OAuth + polling** (moved from Dev 2).
* **Dev 2 (Threat Intel Lead - Java/Rust):** ScanService, VirusTotal integration, email header/attachment analysis, Rust Firewall Daemon, classifier [P1].
* **Dev 3 (Mobile Lead - Kotlin):** Android UI, Biometrics, Notification Listener, SIM detection [P1].
* **Dev 4 (Frontend Lead - Next.js):** Login UI (Passkeys + Turnstile), SOC Dashboard, Email & Firewall Editor.

**Progress (2026-10-27):** ticked items are built and covered by tests (backend 220, daemon 19). The daemon was verified end to end in `--dry-run` against the backend, not yet as root; the hour 8 milestone needs that real run and the Android app. `README.md` "Current status" has the detail.

**Rest:** everyone takes one 2–3 h sleep slot, staggered in pairs between hours 9 and 15. Nobody sleeps during the hour 8 milestone or Phase 4.

### Phase 1: Foundation & Core Plumbing (Hours 00:00 – 04:00)
* **All:**
  * [x] Initialize monorepo, `docker-compose up -d` (Postgres + Redis)
  * [ ] Named Cloudflare Tunnel + path ingress on your domain (or commit to the localhost fallback)
  * [ ] Get a VirusTotal API key; **check that every demo URL gets ≥ 3 detections** (including `testsafebrowsing.appspot.com`); keep a self-blacklisted domain as fallback
  * [ ] Gmail OAuth consent screen in Testing mode with all demo accounts as test users
* **Dev 1:**
  * [x] Repo scaffolding: `.gitignore`, `.env.example`, `docs/protocol.md` (agree on WS + scan JSON as a team)
  * [x] `db/init.sql` (incl. Spring WebAuthn tables) + JPA entities, `ddl-auto=validate`
  * [x] `SecurityConfig.java` (Argon2id + `__Host-session` Redis cookies)
  * [x] `/api/v1/rules` CRUD with upsert + `RulePrecedence`
* **Dev 2:**
  * [x] `/api/v1/scan/unified` with PostgreSQL lookup + Redis cache + **stubbed** VirusTotal client (fixed verdicts)
  * [x] `daemon-rust` skeleton: WebSocket connect + reconnect loop
* **Dev 3:**
  * [ ] Jetpack Compose scaffold, bottom navigation, `BiometricPrompt` lock screen
  * [ ] Login screen + API client (bearer token)
* **Dev 4:**
  * [x] Next.js shell (`shadcn/ui`), sidebar navigation
  * [x] Login page with Turnstile widget
  * [x] `/api` proxy config for localhost fallback

### Phase 2: Walking Skeleton, End to End with Fakes (Hours 04:00 – 08:00)
* **Dev 1:**
  * [x] `FirewallWebSocketHandler` + agent token auth + `SYNC` on connect
  * [x] `DashboardWebSocketHandler` (`/ws/dashboard`, session auth) relaying the same Redis events
  * [x] Publish to Redis on every rule mutation
* **Dev 2:**
  * [x] Daemon handles `SYNC` / `BLOCK` / `BYPASS`
  * [x] `validate.rs` (IP/domain parsing, no shell)
  * [x] `/etc/hosts` atomic sinkhole + `OBSIDIAN` iptables chain + cleanup on exit
* **Dev 3:**
  * [ ] `NotificationListenerService` for WhatsApp + SMS (`MessagingStyle` parsing, dedupe)
  * [ ] Display-name contact lookup (`READ_CONTACTS`) + URL extraction → `/scan/unified`
* **Dev 4:**
  * [x] Blacklist/Whitelist Editor wired to `/api/v1/rules` with Report/Bypass
* **🚩 Hour 8 milestone (whole team checks this together):**
  * [ ] Add a domain in the web editor → daemon blocks `curl` to it on the laptop
  * [ ] Report a URL from the phone → same block happens
  * [ ] Bypass → `curl` succeeds again

### Phase 3: Real Channels (Hours 08:00 – 14:00)
* **Dev 1:**
  * [x] Spring Security WebAuthn passkeys (JDBC repositories) + Turnstile server-side verification
  * [x] Mobile password login → bearer token
  * [x] Gmail OAuth flow + polling worker, handing raw messages to `ScanService`
* **Dev 2:**
  * [x] Real VirusTotal v3 client: GET + POST/poll for unknown URLs, 4 req/min limiter, quota fallback → `PENDING`
  * [x] `Authentication-Results` parser (topmost `mx.google.com` header only) + attachment SHA-256 hashing
* **Dev 3:**
  * [ ] Threat cards with **Report** & **Bypass** buttons
  * [ ] High-priority alert notifications for malicious verdicts
* **Dev 4:**
  * [x] Email Triage page: SPF/DKIM/DMARC badges, VirusTotal scores, 1-click **Bypass (Whitelist)** / **Report (Blacklist)**
  * [x] Live threat feed over `/ws/dashboard` + **"Rust Agent Connected"** heartbeat indicator in the navbar

### Phase 4: Integration, Chaos Testing & P1 Features (Hours 14:00 – 18:00)
* **All (first):**
  * [ ] Connect Android App and Next.js Webapp to the public tunnel URL
  * [ ] Full **Crowd-Immunity Loop**: Report a link on the Android app $\longrightarrow$ PostgreSQL update $\longrightarrow$ Next.js Firewall table updates $\longrightarrow$ Rust Daemon blocks `curl` on the laptop
  * [x] VirusTotal quota exhausted → cached/DB verdicts still work
  * [x] Kill the daemon, add rules, restart → `SYNC` applies the missed rules
  * [ ] Pre-warm `vt:cache:*` with every demo URL/hash
* **P1, only if the above passes:**
  * [ ] Dev 1: TOTP MFA + QR enrollment
  * [ ] Dev 2: Heuristic/LLM classifier (Branch B) + `BypassExpiryJob`
  * [ ] Dev 3: SIM change detection
  * [ ] Dev 4: TTL bypass UI + audit log view
  * [ ] Whoever finishes first (likely Dev 1): Code Guard webhook + gitleaks/OSV push scan + release hashes → VirusTotal + Check Run; Dev 4 adds the `code/` page

### Phase 5: UI Polish, Seed Data & Pitch Rehearsal (Hours 18:00 – 24:00)
* [ ] Pre-populate PostgreSQL with realistic enterprise threat telemetry so charts and logs look rich
* [ ] **Hour 20: record a full backup demo video**
* [ ] Prepare demo network: phone hotspot rather than venue Wi-Fi; secure DNS disabled in the demo browser
* [ ] **Hour 22:30: code freeze**
* [ ] Rehearse the 3-minute live demo 5 times

---

## 8. Winning 3-Minute Live Demo Script for Judges

1. **0:00 – 0:20 | Zero-Trust Login & Hardware Security:**
   * Log in to the Desktop Webapp (behind **Cloudflare Turnstile**) with a **Passkey**. Say in one sentence that the session is a host-only `__Host-` cookie backed by Redis. Don't open DevTools.
   * Unlock the **Android Native App** with the **fingerprint sensor**.
2. **0:20 – 1:10 | Live Email & WhatsApp/SMS Interception:**
   * Show an official email passing SPF/DKIM/DMARC untouched, alongside a spam email containing a malicious link flagged by **VirusTotal**.
   * Send a live WhatsApp message from a saved contact containing a pre-verified phishing URL. Show the Android app ignoring normal chat from that contact but immediately catching and flagging the malicious link.
3. **1:10 – 2:10 | The B2B Rust Firewall Daemon "Wow" Moment:**
   * Take an unflagged phishing email or SMS and click **"Report Malicious"**.
   * Split-screen the **Next.js Blacklist Editor** and a terminal running the **Rust Firewall Daemon**. Show the reported domain appearing in the editor and the daemon instantly applying the firewall rule.
   * Run `curl` against the domain on the laptop: **blocked at the OS level**.
4. **2:10 – 2:40 | User Bypass:**
   * Click **"Bypass (Add to Whitelist)"** on the dashboard. Show the Rust daemon immediately lifting the block and `curl` succeeding again.
5. **2:40 – 3:00 | Wrap-Up:**
   * *"One report from one phone just protected every machine in the organization, in under a second. Obsidian covers email, messaging and the network with one rule set, and it's built on passkeys, Argon2id and a daemon that validates every input before touching the OS."*
   * One line on the roadmap: Windows/macOS daemons, Workspace-wide scanning, device posture checks.

**If the live demo fails:** switch to the backup video immediately. Don't debug on stage.

### Likely Judge Questions (have answers ready)
* *"Does SIM detection stop SIM swaps?"* → It detects SIM changes on this device. A true swap moves the number off this device; loss of service is the stronger signal (see Module 3).
* *"What if the server sends a malicious rule to the daemon?"* → Every value is parsed as an IP or domain and passed as an argument, never through a shell. Agents authenticate with per-device tokens.
* *"Can't browsers bypass `/etc/hosts`?"* → Yes, with DNS-over-HTTPS. Known-bad IPs are still dropped by `iptables`, and a DNS/VPN filtering layer for domains is on the roadmap.
* *"What about VirusTotal rate limits?"* → Redis cache with 24 h TTL, a client-side limiter, crowd-reported rules checked first, and `PENDING` instead of a false `SAFE`.
* *"Can Code Guard hide a leaked API key?"* → No, and nothing can: once pushed, it's compromised. Code Guard catches it within seconds of the push, never stores the value, and drives rotation. Blocking the push itself needs pre-receive hooks, which is on the roadmap.
