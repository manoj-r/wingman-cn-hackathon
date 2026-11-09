# Organizer and registration UI (M5)

An additive local Next.js/TypeScript/Tailwind app. The original starter in `../app/` and its deployment stay independent.

Use Node 22 or newer. From `web/`:

```sh
npm ci
npm run dev
```

Open http://localhost:3000. Start the backend separately using `../docs/local-development.md`. Requests to `/api/v1` proxy to loopback port 8081, preserving authorization, cookies and response cookie attributes. Set the server-only `BACKEND_URL` before starting/building Next if the backend is elsewhere. Set the backend's trusted `PUBLIC_APP_URL=http://localhost:3000` for local registration links. A localhost QR cannot be opened from a phone.

Routes: `/conferences/new`, `/organizer`, `/conferences/{id}/register`. Save both the conference ID and one-time organizer token privately. Tokens are never put into links or browser storage. Organizer reload requires token re-entry. An unchanged registration retry reuses its idempotency key in tab memory; changing the profile creates a new key. Reloading loses the key, so retry an uncertain response before reloading. Conference creation has no backend idempotency contract; after an uncertain creation response, another submission may create another conference.

`/connections` is an explicit registration confirmation handoff for M6, not a recommendations implementation. The M2 endpoints are not yet implemented in this worktree's backend; frontend contract tests use a synthetic local server and do not prove PostgreSQL persistence or real matching.

Validation:

```sh
npm run typecheck
npm run build
npx playwright install chromium
npm run test:e2e
```

The four browser tests run at 375px and exercise real Next proxy requests to the contract fixture: QR image decoding, date validation, private organizer access/count, missing conference, attendee validation, idempotent retries and HttpOnly cookie round-trip. No real credentials or attendee data are used; tracing is disabled so capability values do not enter trace artifacts. Test fixture ports are 3100 and 8189. Dependency versions are pinned in `package-lock.json`; there is no additional lint framework.
