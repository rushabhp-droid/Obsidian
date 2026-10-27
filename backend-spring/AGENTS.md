# Backend instructions

- `RuleService` is the only writer of `threat_rules`. Route reports, bypasses, and admin changes through it so persistence, audit, and Redis publication stay together.
- `RulePrecedence` is the sole implementation of rule ordering. Normalize indicators before persistence and lookup.
- Keep JPA entities and `../db/init.sql` aligned; Hibernate runs with `ddl-auto=validate`.
- Keep one scan endpoint, `POST /api/v1/scan/unified`. Unknown or quota-limited VirusTotal results return `PENDING`, never `CLEAN`.
- Trust only the topmost `Authentication-Results` from `mx.google.com`; official-sender verification also requires the allowlist.
- Use Spring Security's built-in WebAuthn endpoints. Do not add custom passkey endpoints.
- Return API errors through the RFC 7807 handler. Never log credentials, bearer tokens, Gmail refresh tokens, or detected secret values.
- Run backend checks with `./gradlew test`; tests use embedded PostgreSQL and in-process Redis.
