# Obsidian protocol

The contract between Spring (`backend-spring`), the Rust daemon (`daemon-rust`), the web dashboard
(`web-dashboard`) and the Android app (`mobile-android`). The dashboard and daemons are the B2B service
on `/api/v1`; the Android app is the B2C service on its own API, [`/api/b2c/v1`](#b2c-app-api-apib2cv1). This file is the source of truth for shared HTTP, scan, and WebSocket message shapes.
If a shape changes, update every consumer and its contract tests in the same change. Shared workflow rules are in [`AGENTS.md`](../AGENTS.md).

- All JSON. Timestamps are ISO 8601 UTC strings (`2026-10-27T10:15:00Z`). IDs are UUID strings.
- Absent values are `null`, not omitted.
- Errors are RFC 7807 `application/problem+json`: `{"type","title","status","detail","instance"}`. Show `detail`.

## Indicators

| `indicatorType` | Normalized value | Accepted |
| --- | --- | --- |
| `DOMAIN` | lowercase, no scheme, path or trailing dot | `login-verify.example` |
| `IP` | lowercase | one IPv4 or IPv6 address, no port or range |
| `URL` | lowercase | `http://` or `https://` only |
| `EMAIL` | lowercase | `billing@invoice-verify.example` |
| `PHONE` | lowercase, spaces, dashes and brackets removed | `+15550100` (6–15 digits) |
| `FILE_HASH` | lowercase | SHA-256, 64 hex characters |

Values are trimmed and lowercased before they are stored or looked up. A domain rule covers exactly that
host: `/etc/hosts` can't cover subdomains, so the scanner doesn't match them either. Only `DOMAIN` and
`IP` rules reach the daemons. Reporting a URL doesn't block its domain. To block a link on the network,
report its host as a `DOMAIN` (the web dashboard offers the link domain as its own choice for this).

## Authentication

| Client | How |
| --- | --- |
| Web | `__Host-session` cookie (`Secure; HttpOnly; Path=/; SameSite=Lax`, no `Domain`), from password or passkey login. Mutations send `X-XSRF-TOKEN` with the value of the `XSRF-TOKEN` cookie. |
| Mobile client of `/api/v1` | `Authorization: Bearer obm_<64 hex>` from `POST /api/v1/auth/login` with `"client": "MOBILE"`. Valid for 30 days. No CSRF header. The Android app no longer uses this; it signs in on `/api/b2c/v1`. |
| Android (B2C) | `Authorization: Bearer obc_<64 hex>` from `/api/b2c/v1/auth/*`. Valid for 30 days. Only works on `/api/b2c/**`, and `obm_` tokens and session cookies don't work there. |
| Daemon | `Authorization: Bearer obs_<64 hex>` on the `/ws/firewall` handshake, from `POST /api/v1/agents`. |

CSRF isn't checked on `POST /api/v1/auth/login`, `POST /api/v1/auth/register`,
`POST /webauthn/authenticate/options`, `POST /login/webauthn`, or on requests that carry a bearer token.

Cloudflare Turnstile is checked on register and login when the server has `TURNSTILE_SECRET_KEY` set:
the `turnstileToken` body field for password requests, and the `CF-Turnstile-Response` header on
`POST /login/webauthn` for passkeys. A failed check is `400`. Tokens are single use.

### `POST /api/v1/auth/register`

```json
{ "email": "avery.lin@aster.example", "password": "correct horse battery", "displayName": "Avery Lin",
  "organizationName": null, "turnstileToken": "…", "client": "WEB" }
```

- `password` is optional for web (passkey-only account: enroll a passkey right after). 12–128 characters.
  Required when `client` is `MOBILE`.
- `organizationName` creates an organization with the caller as `ADMIN`. Without it the account is `CONSUMER`.
  Employees are created by `scripts/seed.sql` (fleet management is [ROADMAP]).
- `201` with `SessionInfo` (web, session cookie set) or `MobileToken` (mobile). `409` if the email exists.

### `POST /api/v1/auth/login`

```json
{ "email": "avery.lin@aster.example", "password": "…", "turnstileToken": "…", "client": "WEB" }
```

`200` with `SessionInfo` (web) or `MobileToken` (mobile). `401` for a wrong email or password.

```json
{ "token": "obm_6f1c…", "expiresAt": "2026-11-26T10:15:00Z" }
```

### `POST /api/v1/auth/logout`

`204`. Ends the web session, or revokes the bearer token it was called with.

### `GET /api/v1/auth/me`

`200` with `SessionInfo`, `401` when signed out.

```json
{ "email": "avery.lin@aster.example", "displayName": "Avery Lin", "role": "ADMIN",
  "organization": { "id": "6f1c2a4e-…", "name": "Aster Research", "policyAllowUserBypass": true } }
```

`organization` is `null` for consumers.

### Passkeys (Spring Security built-in)

| Endpoint | Notes |
| --- | --- |
| `POST /webauthn/register/options` | Signed in. Returns `PublicKeyCredentialCreationOptions` with base64url fields. |
| `POST /webauthn/register` | Signed in. Body `{"publicKey":{"credential":{…},"label":"…"}}`. Returns `{"success":true}`. |
| `POST /webauthn/authenticate/options` | Returns `PublicKeyCredentialRequestOptions` (discoverable credentials). |
| `POST /login/webauthn` | Body is the assertion `PublicKeyCredential` JSON. Returns `{"authenticated":true,"redirectUrl":"/"}` and sets the session cookie, or `401`. |

The RP ID is `RP_ID` (the tunnel hostname, or `localhost`). Allowed origin is `PUBLIC_BASE_URL`.

## Rules

`ThreatRule`:

```json
{ "id": "4a0e5c7f-…", "orgId": "6f1c2a4e-…", "userId": null, "indicatorType": "DOMAIN",
  "indicatorValue": "testsafebrowsing.appspot.com", "listType": "BLACKLIST", "threatCategory": "PHISHING",
  "source": "USER_REPORT", "confidenceScore": 100, "expiresAt": null, "updatedAt": "2026-10-27T10:15:00Z" }
```

Scope is set by the caller's role. Report and Bypass upsert one row per (scope, indicator) and flip its
`listType`. They never create duplicates.

| Role | Report | Bypass |
| --- | --- | --- |
| `ADMIN` | org scope, `ADMIN_POLICY` | org scope, `ADMIN_POLICY`, `ttlMinutes` optional |
| `EMPLOYEE` | org scope, `USER_REPORT` (blocks for the whole org) | user scope, `USER_BYPASS`, `ttlMinutes` required; `403` if the org disallows user bypass |
| `CONSUMER` | user scope, `USER_REPORT` | user scope, `USER_BYPASS`, `ttlMinutes` optional |

A report or bypass by a non-admin can't change an org rule whose source is `ADMIN_POLICY`: `409`.
Every change writes `audit_logs` and publishes on Redis (see "Redis").

Precedence when several rules match one indicator (first match wins): org `ADMIN_POLICY` → the user's
unexpired `WHITELIST` (only if the org allows user bypass) → the user's `BLACKLIST` → other org rules →
global rules (`orgId` and `userId` both `null`) → VirusTotal. Expired rules are ignored everywhere.

| Endpoint | Body | Returns |
| --- | --- | --- |
| `GET /api/v1/rules` | | `ThreatRule[]`, unexpired, newest first: global, your org's and your own (admins also see employees' rules) |
| `POST /api/v1/rules/report` | `{"indicatorType","indicatorValue","threatCategory"?}`; category `PHISHING`, `MALWARE`, `SPAM` or `MALICIOUS` (default) | `ThreatRule` |
| `POST /api/v1/rules/bypass` | `{"indicatorType","indicatorValue","ttlMinutes"?}`; 1–1440 minutes | `ThreatRule` (`threatCategory` `SAFE`) |

## Scanning

### `POST /api/v1/scan/unified`

The only scan endpoint, for every channel. Android sends a bearer token; the Gmail worker calls the same
service in-process.

```json
{ "channel": "WHATSAPP", "sender": "Priya Nair", "isSavedContact": true,
  "text": null, "urls": ["http://testsafebrowsing.appspot.com/s/phishing.html"], "fileHashes": [] }
```

- `channel`: `SMS`, `WHATSAPP` or `EMAIL`.
- Saved contact (`isSavedContact: true`): send URLs only. A request with `text` is rejected with `400`.
- Unknown sender: send `text` and `urls`. URLs found in `text` are scanned too. Text is never stored.
- Mobile never sends `fileHashes` (notifications carry no attachments).
- At most 20 URLs, 20 hashes and 5,000 characters of text.

```json
{ "verdict": "MALICIOUS", "category": "PHISHING", "vtDetections": 9,
  "scanId": "c1000006-…",
  "indicators": [
    { "indicatorType": "URL", "value": "http://testsafebrowsing.appspot.com/s/phishing.html",
      "verdict": "MALICIOUS", "category": "PHISHING", "vtDetections": 9, "source": "VIRUSTOTAL", "ruleId": null },
    { "indicatorType": "PHONE", "value": "+15550100", "verdict": "MALICIOUS", "category": "SPAM",
      "vtDetections": 0, "source": "RULE", "ruleId": "4a0e5c7f-…" } ] }
```

- `verdict`: `MALICIOUS`, `SUSPICIOUS`, `PENDING` or `CLEAN`, the worst across indicators (in that order).
- `PENDING` means not known yet: VirusTotal is still analysing, the quota is used up, or VirusTotal
  doesn't know the file. It is never reported as `CLEAN`.
- `category` is the worst indicator's category, `null` for `CLEAN` and `PENDING`.
- `vtDetections` is the highest VirusTotal `malicious` count across indicators.
- `source`: `RULE` (a `threat_rules` row decided it), `VIRUSTOTAL` (cached or live), `NONE` (no rule, no
  VirusTotal result).
- The sender (a phone number for SMS, an address for email) is checked against rules only, and is listed in
  `indicators` only when a rule matched it. WhatsApp display names aren't looked up.

Lookup order for each indicator: `threat_rules` with precedence (a URL is checked as the URL, then its
host as `DOMAIN`/`IP`; an email sender as the address, then its domain) → Redis `vt:cache:*` →
VirusTotal v3. Thresholds on the `malicious` count: 0 = `CLEAN`, 1–2 = `SUSPICIOUS`, 3+ = `MALICIOUS`.
0 malicious with 1+ `suspicious` engines is `SUSPICIOUS`. A whitelisted indicator is `CLEAN` in the
response and `BYPASSED` in `scan_logs`.

### Dashboard reads

| Endpoint | Returns |
| --- | --- |
| `GET /api/v1/scans` | `ScanLog[]`, newest first, at most 200. Admins see their org's users; others see their own. |
| `GET /api/v1/emails` | `ScannedEmail[]` for your own Gmail, newest first, at most 100 |
| `GET /api/v1/integrations/gmail` | `GmailConnection` |
| `POST /api/v1/integrations/gmail/disconnect` | `204`; deletes the refresh token |
| `GET /oauth2/authorization/google` | `302` to Google consent (signed in) |
| `GET /login/oauth2/code/google` | OAuth callback; `302` to `/settings?gmail=connected` or `/settings?gmail=error` |

```json
{ "id": "c1000006-…", "channel": "WHATSAPP", "sender": "Priya Nair", "indicatorChecked": "http://testsafebrowsing.appspot.com/s/phishing.html",
  "verdict": "MALICIOUS", "vtPositives": 9, "createdAt": "2026-10-27T10:15:00Z" }
```

```json
{ "id": "e5000004-…", "from": "Payroll Team <payroll@aster-payroll.example>", "subject": "Action required",
  "receivedAt": "2026-10-27T09:10:00Z", "spf": "FAIL", "dkim": "NONE", "dmarc": "FAIL", "verifiedOfficial": false,
  "verdict": "MALICIOUS",
  "links": [ { "url": "http://login-verify.aster-payroll.example/reset", "verdict": "MALICIOUS", "vtDetections": 14 } ],
  "attachments": [ { "filename": "invoice.pdf.exe", "sha256": "5f2b…", "verdict": "MALICIOUS", "vtDetections": 31 } ] }
```

```json
{ "connected": true, "address": "avery.lin@aster.example", "lastPolledAt": "2026-10-27T10:14:35Z" }
```

`spf`, `dkim`, `dmarc` are `PASS`, `FAIL` or `NONE`, parsed only from the topmost `Authentication-Results`
header added by `mx.google.com`. `verifiedOfficial` needs all three to pass and the sender domain on the
allowlist (`obsidian.email.official-domains`) or whitelisted by a rule. Verified official email skips link
and attachment scanning.

## Agents

| Endpoint | Body | Returns |
| --- | --- | --- |
| `POST /api/v1/agents` | `{"hostname":"build-linux-01"}` (admins) | `201` `RegisteredAgent`: the token is shown once, the server keeps its SHA-256 |
| `GET /api/v1/agents` | | `EndpointAgent[]` for your organization (`[]` for consumers) |

```json
{ "id": "a9000001-…", "hostname": "build-linux-01", "lastHeartbeatAt": null, "createdAt": "2026-10-27T10:15:00Z",
  "token": "obs_3f9c…" }
```

An agent is online when its last heartbeat is under 30 s old.

## WebSocket `/ws/firewall` (daemon)

Handshake: `GET /ws/firewall` with `Authorization: Bearer obs_…`. Anything else gets `401`.

Server → daemon:

```json
{ "type": "SYNC", "rules": [ { "ruleId": "4a0e5c7f-…", "indicator_type": "DOMAIN", "value": "testsafebrowsing.appspot.com" },
                             { "ruleId": "4a0e5c7f-…", "indicator_type": "IP", "value": "203.0.113.45" } ] }
{ "type": "BLOCK",  "ruleId": "4a0e5c7f-…", "indicator_type": "DOMAIN", "value": "testsafebrowsing.appspot.com" }
{ "type": "BYPASS", "ruleId": "4a0e5c7f-…", "indicator_type": "DOMAIN", "value": "testsafebrowsing.appspot.com" }
```

- `SYNC` is sent on every connect. It is the organization's full set of indicators to block right now:
  its org-level and global `IP`/`DOMAIN` rules after precedence. The daemon removes everything it manages
  and applies exactly this set.
- `BLOCK` and `BYPASS` follow every org-level `IP`/`DOMAIN` change, and every global one (a promoted
  B2C consumer report). A global change is decided per organization with rule precedence, so an
  organization whose admin allows the indicator gets no `BLOCK`. User-level rules never reach daemons.

Daemon → server: `{"type":"HEARTBEAT"}` every 10 s. Each one updates `endpoint_agents.last_heartbeat_at`.
`ACK` (`{"type":"ACK","ruleId":"…","appliedMs":12}`) is [P1]. Both sides ignore types they don't know.

## WebSocket `/ws/dashboard` (web)

Authenticated by the session cookie; the `Origin` must be `PUBLIC_BASE_URL` (or `obsidian.web.allowed-origins`).
The server invalidates the web session 25 seconds after its last dashboard connection closes. This grace period
lets a page reload or WebSocket reconnect preserve the session.
Server → browser frames (the `DashboardEvent` union in `web-dashboard/src/lib/types.ts`):

```json
{ "type": "BLOCK", "ruleId": "4a0e5c7f-…", "indicator_type": "DOMAIN", "value": "testsafebrowsing.appspot.com", "at": "2026-10-27T10:15:00Z" }
{ "type": "SCAN", "scan": { "id": "…", "channel": "SMS", "sender": "+15550100", "indicatorChecked": "…", "verdict": "SUSPICIOUS", "vtPositives": 2, "createdAt": "…" } }
{ "type": "HEARTBEAT", "agentId": "a9000001-…", "hostname": "build-linux-01", "at": "2026-10-27T10:15:00Z" }
{ "type": "CODE_FINDING", "finding": { …CodeFinding… } }
```

`CODE_FINDING` frames go to the organization's admins only.

Rule events cover every indicator type, so rule lists know to refetch. Org-level events reach everyone in the
organization; user-level events reach that user and the organization's admins.

## Code Guard [P1]

A GitHub App per organization (Contents read, Checks write, Metadata read; events push, pull request,
release). Findings belong to the organization of `CODEGUARD_ORG_ADMIN_EMAIL`.

| Endpoint | Notes |
| --- | --- |
| `POST /api/v1/webhooks/github` | GitHub only. `X-Hub-Signature-256` is checked (HMAC-SHA256 of the raw body, constant-time) before anything is parsed: `401` if it doesn't match, `503` if Code Guard isn't configured, otherwise `202` and the scan runs asynchronously |
| `GET /api/v1/code/findings?repo=&type=&severity=&status=` | Admins. `CodeFinding[]`, newest first, at most 500 |
| `POST /api/v1/code/findings/{id}/status` | Admins. `{"status":"REVOKED\|FALSE_POSITIVE"}`; writes `audit_logs` (`CODE_FINDING_STATUS`) |

```json
{ "id": "f7000001-…", "repoFullName": "acme/payments-api", "trigger": "PUSH", "gitRef": "9c41e2b…",
  "findingType": "SECRET", "severity": "CRITICAL", "ruleId": "aws-access-token", "location": "config/settings.py:3",
  "secretFingerprint": "3f8a1c9e…", "status": "OPEN", "createdAt": "2026-10-27T10:15:00Z" }
```

- **Push and pull request** (`opened`, `synchronize`, `reopened`): the commit's zipball is extracted into a fresh
  temp dir (deleted afterwards), then `gitleaks detect --no-git` finds leaked secrets (`SECRET`, `CRITICAL`) and
  `osv-scanner --recursive` finds vulnerable dependencies (`VULN_DEPENDENCY`, severity from CVSS: ≥9 critical,
  ≥7 high, ≥4 medium). A Check Run "Obsidian Code Guard" is posted: `failure` with any critical or high finding,
  `neutral` when a scanner isn't installed, else `success`, with up to 50 file/line annotations.
- **Release** (`published`): each asset (up to 100 MB) is hashed while streaming and looked up through the shared
  VirusTotal client: 3+ detections is `MALICIOUS_BINARY` `CRITICAL`, 1–2 is `MEDIUM`; `ruleId` is `VT:<detections>`.
  Unknown files stay `PENDING` and get no finding.
- A secret's value is never stored, logged or sent. `secretFingerprint` is its SHA-256. A leaked key is
  compromised: the fix is rotating it, then marking the finding `REVOKED`.

## B2C app API (`/api/b2c/v1`)

The Android app's own API: a separate Spring Security filter chain on `/api/b2c/**` (stateless, `obc_`
bearer tokens only, no cookies, no CSRF). Consumers are their own accounts (`consumers` table), not
dashboard `users`. Errors are problem details with an extra `code` field the app can branch on
(`INVALID_CREDENTIALS`, `EMAIL_NOT_VERIFIED`, `ACCOUNT_EXISTS`, `INVALID_CODE`, `TOO_SOON`,
`EMAIL_NOT_CONFIGURED`, `GOOGLE_NOT_CONFIGURED`, `INVALID_GOOGLE_TOKEN`, …).

### Sign-up and sign-in (no token needed)

| Endpoint | Body → response |
| --- | --- |
| `GET /status` | → `{"server":"UP","database":"UP\|DOWN","cache":"UP\|DOWN","virusTotalConfigured":true,"googleSignInConfigured":true,"googleClientId":"…\|null","emailConfigured":true,"serverTime":"…"}` |
| `POST /auth/register` | `{"name","email","password"}` (name required, password 10–128) → `202 {"email","expiresInSeconds":600}`. Emails a 6-digit code. A still-unverified account is refreshed; a verified one is `409 ACCOUNT_EXISTS`. |
| `POST /auth/verify-email` | `{"email","code"}` → `SignedIn` |
| `POST /auth/resend-code` | `{"email"}` → `202`. At most one code per 45 s (`429 TOO_SOON`). |
| `POST /auth/login` | `{"email","password"}` → `SignedIn`, `401 INVALID_CREDENTIALS`, or `403 EMAIL_NOT_VERIFIED` (a new code is sent) |
| `POST /auth/google` | `{"idToken"}` from Credential Manager, issued to the web OAuth client in `googleClientId` → `SignedIn`. Creates the account on first use, links an existing one by verified email, and drops the password of a linked account that was never verified. |
| `POST /auth/forgot-password` | `{"email"}` → always `202` (doesn't reveal whether the account exists) |
| `POST /auth/reset-password` | `{"email","code","newPassword"}` → `SignedIn` |
| `POST /auth/logout` | revokes the bearer token → `204` |

`SignedIn` is `{"token":"obc_…","expiresAt":"…","consumer":Profile}`; `Profile` (also `GET /me`) is
`{"id","email","displayName","emailVerified","hasPassword","googleLinked","createdAt"}`.

Codes are 6 digits, valid 10 minutes, single use, 5 wrong tries. Only a hash is kept (Redis
`b2c:code:<purpose>:<email>`). They go out over SMTP (`SMTP_*` in `.env`). Without SMTP, `gradle
bootTestRun` logs them instead (`obsidian.b2c.log-codes`); any other server answers `503 EMAIL_NOT_CONFIGURED`.

### Protection (bearer token)

| Endpoint | Body → response |
| --- | --- |
| `POST /scan/url` | `{"url","source":"URL_SCAN\|LINK_OPENED"}` → `ScanResult` |
| `POST /scan/file` | `{"sha256","fileName","sizeBytes"}` → `ScanResult`. The app hashes the file; files are never uploaded. |
| `POST /scan/message` | `{"app","sender","savedContact","text","urls"}` → `ScanResult`. `text` must be null for saved contacts (`400`); it's read for links and never stored. |
| `GET /activity` | newest 500 `{"id","kind":"URL_SCAN\|FILE_SCAN\|LINK_OPENED\|MESSAGE\|REPORT","subject","detail","verdict","threatCategory","riskScore","vtDetections","createdAt"}` |
| `GET /rules`, `POST /rules`, `DELETE /rules/{id}` | personal rules `{"id","indicatorType","indicatorValue","listType","createdAt"}`; `POST` takes `{"indicatorType","indicatorValue","listType"}` and moves an existing rule to the other list |
| `GET /firewall/blocklist` | `{"domains":[…],"ips":[…],"personal":[Rule],"generatedAt"}`: the global `DOMAIN`/`IP` blacklist from `threat_rules` plus the consumer's rules, for the phone firewall |
| `POST /reports` | `{"indicatorType","indicatorValue","threatCategory","note"}` → `Report` |
| `GET /reports` | the consumer's reports, newest first |

`ScanResult` is `{"verdict","category","riskScore","vtDetections","trusted","decidedBy","indicators":[IndicatorResult],"activityId"}`.
An indicator is decided by, in order: the consumer's own whitelist then blacklist, global `threat_rules`
(never organization rules), then VirusTotal. A URL is also matched by its host and a domain by its parent
domains (unlike the B2B scanner, a rule for `example.com` covers `login.example.com`). `decidedBy` is
`PERSONAL_RULE`, `SHARED_RULE`, `VIRUSTOTAL` or `NONE`. Unknown is `PENDING`, never `CLEAN`.
`riskScore` is 0–100: a blocking rule is 95, VirusTotal MALICIOUS is 70 + 3 per engine (max 100),
SUSPICIOUS 40 + 10 per engine (max 69), clean 0, and `null` while `PENDING`. A personal whitelist
match is reported as `CLEAN` with `trusted: true` (stored as `BYPASSED` in Activity).

### Consumer reports and global blocks

`Report` is `{"id","indicatorType","indicatorValue","threatCategory","note","status","reporters","reportersNeeded","createdAt"}`.
A report adds a personal blacklist rule at once. It becomes a global `threat_rules` blacklist row (no org,
no user, written by `RuleService.blockGlobally`, audited as `CONSUMER_REPORT`, published to daemons) when:

1. VirusTotal says `MALICIOUS` (3+ engines) for the URL, domain, IP or file (`source: VIRUSTOTAL`), or
2. `reportersNeeded` (3) different consumers with a verified email reported it within 7 days, or
3. an organization admin approves it in the dashboard.

`status` is `WAITING`, `BLOCKED`, `DISMISSED`, or `PROTECTED`: a `DOMAIN` on the protected list never
becomes a global block from a consumer report, not even with admin approval (block it in an organization's
own rules instead), and a `URL` on a protected host needs an admin. The protected list is the top 1,000
Tranco domains minus shared hosting (`backend-spring/src/main/resources/b2c`), plus admin additions;
subdomains are covered unless they sit under a shared-hosting suffix such as `github.io`. Waiting reports
are re-checked with VirusTotal every 10 minutes. Audit rows hold consumer ids, never consumer emails.

Admin endpoints on `/api/v1` (dashboard session, organization admins only, otherwise `403`):

| Endpoint | Body → response |
| --- | --- |
| `GET /api/v1/consumer-reports` | `[{"indicatorType","indicatorValue","threatCategory","status","protectedDomain","reporters","reportersNeeded","notes":[…],"firstReportedAt","lastReportedAt"}]` (`ConsumerReportQueueItem`), one per indicator, `WAITING` and `PROTECTED` only |
| `POST /api/v1/consumer-reports/approve` | `{"indicatorType","indicatorValue"}` → the global `ThreatRule`; `409` for a protected domain |
| `POST /api/v1/consumer-reports/dismiss` | `{"indicatorType","indicatorValue"}` → `204` |
| `GET /api/v1/protected-domains` | admin-added domains |
| `POST /api/v1/protected-domains` | `{"domain"}` → the updated list |
| `DELETE /api/v1/protected-domains/{domain}` | `204` |

## Redis

| Key / channel | Value |
| --- | --- |
| `channel:firewall:events` | `{"kind":"RULE\|SCAN\|HEARTBEAT\|CODE","orgId":"…\|null","userId":"…\|null","event":{…}}`, where `event` is the `/ws/dashboard` frame |
| `vt:cache:<id>` | `{"verdict":"MALICIOUS","malicious":14,"suspicious":1,"category":"PHISHING","checkedAt":"…"}`, TTL 86,400 s. `<id>` is the lowercase SHA-256 for files, the VirusTotal URL id (unpadded base64url of the URL exactly as scanned), `domain:<domain>` or `ip:<address>` |
| `vt:pending:<url id>` | VirusTotal analysis id of a submitted URL, TTL 3,600 s |
| `vt:notfound:<sha256>` | `1` when VirusTotal doesn't know a file, TTL 3,600 s |
| `mobile:token:<sha256 of token>` | user id, TTL 30 days |
| `b2c:token:<sha256 of token>` | consumer id, TTL 30 days |
| `b2c:code:<verify_email\|reset_password>:<email>` | hash `{hash, attempts}` of a sign-up or reset code, TTL 10 min |
| `b2c:code-sent:<purpose>:<email>` | resend cooldown, TTL 45 s |
| `spring:session:sessions:<id>` | Spring Session (web), TTL 3,600 s |

TTL bypasses live only in `threat_rules.expires_at`, never in Redis.
