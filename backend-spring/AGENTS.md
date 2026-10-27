# Backend instructions

- `RuleService` is the only writer of `threat_rules`. Route reports, bypasses, and admin changes through it so persistence, audit, and Redis publication stay together.
- `RulePrecedence` is the sole implementation of rule ordering. Normalize indicators before persistence and lookup.
- Keep JPA entities and `../db/init.sql` aligned; Hibernate runs with `ddl-auto=validate`.
- Keep one scan endpoint, `POST /api/v1/scan/unified`. Unknown or quota-limited VirusTotal results return `PENDING`, never `CLEAN`.
- Trust only the topmost `Authentication-Results` from `mx.google.com`; official-sender verification also requires the allowlist.
- Use Spring Security's built-in WebAuthn endpoints. Do not add custom passkey endpoints.
- Passkey registration options live in `InMemoryCreationOptionsRepository`, keyed by session id, because `PublicKeyCredentialCreationOptions` isn't `Serializable` and the Redis session can't store it. This assumes a single backend instance.
- `PasskeyLoginGuardFilter` renews the session id at `/webauthn/authenticate/options` and enforces Turnstile on `/login/webauthn`. Keep both when changing the security chain.
- `ApiContractTest` checks DTO field names against `web-dashboard/src/lib/types.ts`, and `EnvExampleTest` fails when a config key the app reads is missing from `.env.example`. Update those files rather than the tests.
- Return API errors through the RFC 7807 handler. Never log credentials, bearer tokens, Gmail refresh tokens, or detected secret values.
- Run backend checks with `./gradlew test`; tests use embedded PostgreSQL and in-process Redis.
