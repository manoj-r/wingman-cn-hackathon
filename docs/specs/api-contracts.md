# Conference Wingman API contracts

Status: proposed JSON REST contract. Browser base `/api/v1`; Next proxies to Spring. UUID identifiers, ISO-8601 UTC timestamps, JSON UTF-8. Responses use explicit DTOs, never JPA entities. All private/creation responses use `Cache-Control: no-store`. Public base URL comes from trusted `PUBLIC_APP_URL`, never an unchecked Host header.

## Shared validation and errors

Trim strings. Conference name 1–120, description 1–2000, location 1–200; startDate/endDate are instants and end must not precede start. Attendee name 1–120, email syntactically valid and at most 254, jobTitle/company 1–160, bio 1–2000, networkingGoals 1–1000; interests 1–10 distinct strings of 1–60 characters. Request body limit 16 KiB. Dates in the past allowed for repeatable fictional demos.

Error shape: `{"error":{"code":"VALIDATION_ERROR","message":"Check the highlighted fields.","fields":{"name":"Required"},"traceId":"opaque-trace-id"}}`. Codes: 400 validation/invalid JSON, 401 missing/invalid capability, 404 absent resource or inaccessible matched target, 409 duplicate registration/idempotency conflict, 429 rate limited, 503 unavailable dependency. Retryable responses include `Retry-After`. Never echo tokens, emails, provider bodies or stack traces. Ownership checks happen before private data reads.

## Create and manage conference

`POST /conferences` is public, rate limited.

Request:
```json
{"name":"Cloud Native Connections","description":"Meet fellow builders","location":"Denver","startDate":"2026-11-09T16:00:00Z","endDate":"2026-11-10T01:00:00Z"}
```

201 response:
```json
{"conference":{"id":"11111111-1111-4111-8111-111111111111","name":"Cloud Native Connections","description":"Meet fellow builders","location":"Denver","startDate":"2026-11-09T16:00:00Z","endDate":"2026-11-10T01:00:00Z"},"registrationUrl":"http://localhost:3000/conferences/11111111-1111-4111-8111-111111111111/register","organizerToken":"<returned-once-private-random-value>"}
```

UI renders QR locally from `registrationUrl` using one small QR dependency; no third-party QR service receives URLs. `GET /conferences/{conferenceId}` returns the public `conference` object only. No tokens or attendees.

`GET /conferences/{conferenceId}/management`, header `Authorization: Bearer <organizerToken>`, returns 200 `{"conference":{...},"registrationUrl":"...","registeredAttendeeCount":6}`. Count registrations from PostgreSQL. Missing/wrong token returns 401; no email directory or token recovery endpoint. Re-entering token after tab reload is acceptable for MVP.

## Register attendee

`POST /conferences/{conferenceId}/registrations`; header `Idempotency-Key: <client-generated-UUID>` required. Store the key and canonical request digest directly on the registration row, with a unique `(conference_id, idempotency_key)` constraint, in the same transaction as the attendee and initial processing state. No separate request entity is required. Identical retry returns the same registration and rotates its session capability securely; different payload with same key returns 409. Only capability digests are persisted. Concurrent conflicts roll back before replay lookup in a fresh transaction; validation failures and rolled-back submissions reserve no key. Do not log the key. A distinct key for an existing conference/email returns 409 with a generic message and never discloses the existing profile or capability.

Request:
```json
{"name":"Alex Chen","email":"alex.chen@example.test","jobTitle":"Platform Engineer","company":"Example Labs","bio":"Building developer platforms and learning observability.","interests":["Kubernetes","OpenTelemetry"],"networkingGoals":"Find peers improving developer experience."}
```

201 response (200 for successful identical replay):
```json
{"registrationId":"22222222-2222-4222-8222-222222222222","conferenceId":"11111111-1111-4111-8111-111111111111","matchingStatus":"PENDING","connectionsPath":"/connections"}
```

Set `wingman_attendee=<opaque-random-capability>; HttpOnly; SameSite=Lax; Path=/api/v1`; add Secure on HTTPS. Cookie lifetime 24 hours; only `session_token_hash` and `session_expires_at` stored directly on registration. One active capability per registration and one active registration session per browser for MVP; a new registration replaces that browser cookie. PostgreSQL failures return 503 without a success response. AI/Elasticsearch failure after persistence does not turn registration into a failed submission.

## Recommendations

`GET /me/matches` requires attendee cookie. Registration/conference are derived from that capability, not client-supplied IDs.

200 response:
```json
{"registrationId":"22222222-2222-4222-8222-222222222222","matchingStatus":"READY","generationMode":"llm","matches":[{"attendeeId":"33333333-3333-4333-8333-333333333333","name":"Jordan Rivera","jobTitle":"SRE","company":"Sample Systems","interests":["OpenTelemetry"],"explanation":"You both work on observability and developer experience.","conversationStarter":"What has helped your team make tracing easier to adopt?"}],"availableMatchCount":1,"retryable":false,"traceId":"opaque-trace-id"}
```

`matches` contains 0–3 entries, no email, raw vector, private bio or tokens. `matchingStatus`: PENDING, PROCESSING, READY, FAILED. Pending/processing returns empty matches and `pollAfterSeconds:2`. READY with zero matches is valid when no eligible peers exist. FAILED has a safe `failureCode` and retryable flag. `generationMode` is `llm`, `template`, or null; templates are visibly labeled degraded output. UI stops automatic polling after 45 seconds and offers manual refresh.

`POST /me/matches/retry` requires cookie, same-origin check and rate limit (one accepted retry per 30 seconds). No body. Returns 202 with status PENDING, or current PROCESSING for already queued work; never creates a duplicate job. Requeues failed or completed matching work for this registration only.

## Friendly introduction

`POST /me/introductions`, attendee cookie and same-origin check required. Request `{"targetAttendeeId":"33333333-3333-4333-8333-333333333333"}`. Target must be in the current stored matches and still registered in the same conference. Rate limit to five requests per minute per session.

200 response: `{"targetAttendeeId":"33333333-3333-4333-8333-333333333333","text":"Hi Jordan, I’m Alex. I’m working on developer platforms and would love to hear how you approach tracing adoption.","generationMode":"llm","delivery":"copy-only"}`. Limit text to 600 characters. No mail, notification or message delivery occurs. Provider failure returns 503 with retry guidance; a UI template option must be clearly labeled.

## Compatibility and operations

Retain starter `GET /healthz`, `GET /readyz`, `GET /api/sessions` and its `{version,sessions}` shape. Proposed backend provides corresponding endpoints and `GET /api/v1/version` with `{version,service}` for future routing. Liveness must not call paid AI. Only the demo profile enables seed/reindex commands; no public reset/import API. No public Elasticsearch, Dash0 credentials or management tokens in client configuration.
