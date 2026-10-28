# Dashboard instructions

- Keep API calls in `src/lib/api.ts` and keep the `mock` and `live` API modes working.
- `NEXT_PUBLIC_*` values are baked into the production build. Rebuild after changing them.
- Keep Spring routing in `next.config.ts` aligned with `../cloudflared/config.yml` for API, WebSocket, WebAuthn, and OAuth paths.
- When a response or request shape changes, update `../docs/protocol.md`, `src/lib/types.ts`, and every consumer together.
- Keep session behavior same-origin; do not expose bearer tokens or message contents in logs.
- Run dashboard checks with `npx --no-install tsc --noEmit` and `npm run lint` from this directory.
- Admins and employees get different menus (`src/components/shell/nav.ts`). Wrap admin-only pages in `AdminOnly`; the API enforces roles regardless (403), so never rely on hiding a link.
- Build pages from what the backend returns. Screens in the Figma file that nothing backs (binary analysis, investigations, PR review) are left out rather than filled with sample data.
