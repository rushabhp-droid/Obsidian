# web-dashboard

Next.js 15 console for Obsidian: the B2C portal and the B2B SOC dashboard. The UI comes from the
[Obsidian Figma file](https://www.figma.com/design/J6sNqmkJhWv92gq119Isp5/Obsidian): its tokens, shared
components and the P0 screens.

## Run

```sh
npm install
cp .env.example .env.local   # optional
npm run dev                  # http://localhost:3000
```

`NEXT_PUBLIC_API_MODE` defaults to `mock`: pages read sample data from `src/lib/mock-store.ts`, and Report,
Bypass and agent registration change that data in the browser only. Set it to `live` once Spring runs on
`SPRING_ORIGIN` (default `http://localhost:8080`). `next.config.ts` then rewrites `/api`, `/ws`, `/webauthn`,
`/login/webauthn`, `/oauth2` and `/login/oauth2` to Spring, and `middleware.ts` sends visitors without a
`__Host-session` cookie to `/login`.

The dashboard signs out after its last tab closes. A same-tab reload keeps the session; opening the dashboard
in a new tab after the last tab closed signs out and returns to `/login`. In live mode, Spring also invalidates
the Redis session 25 seconds after the final `/ws/dashboard` connection closes, allowing a brief reconnect. In
mock mode, browser storage simulates the tab behavior; authentication itself is not sent to Spring.

## Screens

| Route | Figma frame | Notes |
| --- | --- | --- |
| `/login` | Sign in | Passkey (Spring WebAuthn), password, Turnstile when a site key is set |
| `/dashboard` | Security overview | Scan verdicts, rule changes, agent heartbeat |
| `/threats` | Threat monitoring | Unified feed with live `/ws/dashboard` events, Report / Bypass |
| `/emails` | Email security | SPF / DKIM / DMARC, VirusTotal scores, Report / Bypass |
| `/agents` | Endpoint inventory | Register a daemon, token shown once, heartbeat status |
| `/firewall` | Rules and policies | Blacklist / whitelist editor |
| `/settings` | Integration settings | Connect Gmail, add a passkey |

Not built yet: Authentication challenge (TOTP, [P1]) and Audit log and record detail ([P1]). Code Guard
screens are implemented for admins. The other Figma frames (binary and artifact analysis, investigations, reports, notifications,
users and roles, organization settings, endpoint detail, destination review, messaging security, the privacy
policy and the compact views) are outside the current implementation scope.

## API contract

Types live in `src/lib/types.ts` and follow [`docs/protocol.md`](../docs/protocol.md), which wins; change both
together. `backend-spring`'s `ApiContractTest` reads `types.ts` and fails when a response drifts from it.
Besides the endpoints in [`docs/protocol.md`](../docs/protocol.md), the pages use these reads, all implemented in Spring:

| Endpoint | Used by |
| --- | --- |
| `GET /api/v1/auth/me` | Shell (user, organization, role) |
| `POST /api/v1/auth/logout` | Account menu |
| `GET /api/v1/scans` | Overview, Threat monitoring, Settings |
| `GET /api/v1/agents` | Shell heartbeat indicator, Endpoint inventory |
| `GET /api/v1/emails` | Email security |
| `GET /api/v1/integrations/gmail`, `POST …/gmail/disconnect` | Email security, Settings |

- `POST /api/v1/auth/login` body is `{ email, password, turnstileToken }`; `400` means the Turnstile check failed.
  Passkey login sends the Turnstile token in a `CF-Turnstile-Response` header.
- Mutations send `X-XSRF-TOKEN` from the `XSRF-TOKEN` cookie, which Spring sets on every response.
- Errors are problem details; `api.ts` shows their `detail`.
- `/ws/dashboard` frames are the `DashboardEvent` union. The Next.js rewrite proxies the WebSocket upgrade too,
  so `NEXT_PUBLIC_WS_URL` isn't needed on localhost.
- Bypass sends `ttlMinutes: 15` for `EMPLOYEE` sessions, because enterprise user bypasses must expire. Picking
  the TTL is the [P1] TTL feature.

Live mode against the no-Docker backend: `cd backend-spring && gradle bootTestRun`, then
`NEXT_PUBLIC_API_MODE=live npm run dev`, and sign in as `avery.lin@aster.example` / `obsidian-demo-2026`.

## Design system

Figma tokens are CSS variables in `src/app/globals.css`, and shadcn's variables are mapped onto them. Inter and
IBM Plex Mono come from `next/font`. The shadcn components in `src/components/ui` are restyled to the Figma
"Shared component reference": 36 px controls, 6 px control radius, 4 px badges, 8 px panels and 54 px table
rows. Obsidian-specific pieces are in `src/components/obsidian`. The design is light-only. Color always
appears next to a text label, and green is reserved for verified states, so `CLEAN` renders neutral.
