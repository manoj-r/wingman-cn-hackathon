# Conference Wingman data model

Status: proposed schema. PostgreSQL owns identity, registration, capabilities and processing state. Flyway creates schema; JPA validates rather than auto-mutating it. All IDs UUID, times `timestamptz` in UTC.

## Relational entities

| Entity/table | Fields and constraints |
| --- | --- |
| Conference / `conference` | id PK; name varchar(120); description text; location varchar(200); start_date/end_date timestamps with end >= start; organizer_token_hash unique bytea (32 bytes); created_at timestamp. All non-null. API field `organizerToken` is write-once output only; raw token is never a database column. |
| Attendee / `attendee` | id PK; name varchar(120); email varchar(254), normalized lowercase; job_title/company varchar(160); bio text; networking_goals text; created_at timestamp. All non-null. |
| Attendee interests / `attendee_interest` | attendee_id FK cascade, ordinal integer, interest varchar(60); PK(attendee_id, ordinal), unique(attendee_id, interest). Map as JPA element collection, preserve order. |
| Registration / `registration` | id PK; conference_id FK; attendee_id FK; normalized_email varchar(254); idempotency_key UUID; request_hash bytea (32-byte SHA-256 digest of canonical request, including normalized email); created_at timestamp. All non-null. unique(conference_id, attendee_id); unique(attendee_id) for the MVP's per-registration profile policy; unique(conference_id, normalized_email); unique(conference_id, idempotency_key). |
| Processing / `registration_processing` | registration_id PK/FK cascade; status PENDING/PROCESSING/READY/FAILED; embedding_status PENDING/READY/FAILED; index_status PENDING/READY/FAILED; attempts integer; lease_until; next_attempt_at; safe_error_code; embedding_model; embedding_dimensions; index_version; updated_at. |
| Session / `attendee_session` | registration_id unique FK cascade; capability_hash unique bytea; expires_at timestamp. One active capability per registration; rotate on idempotent replay. |
| Match snapshot / `registration_match` | source_registration_id FK cascade; target_registration_id FK cascade; rank 1–3; explanation varchar(600); conversation_starter varchar(400); generation_mode; generated_at; PK(source_registration_id,rank); unique(source_registration_id,target_registration_id); self-match forbidden. |

### Identity and duplicate policy

No global attendee account or public email lookup. For simplicity, create a distinct attendee profile per conference registration. Enforce email uniqueness within a conference using `conference_id` plus `normalized_email` in `registration` (an intentional denormalization of attendee email); normalize once in the service and insert both values in the transaction. This lets the same email join different conferences without overwriting another profile. A unique constraint resolves concurrent duplicates, mapped to generic 409. Registration and attendee inserts roll back together on conflict.

Idempotency belongs to the registration itself: one successful request creates one registration, so no separate request entity is needed. Persist key and request digest in the same transaction as attendee, registration and initial processing state. An identical replay finds the registration by conference/key, compares the canonical request digest, and rotates its session capability; a changed payload returns 409. On a concurrent unique-constraint conflict, roll back the losing transaction before looking up the committed registration in a fresh transaction and applying the same replay rules. Failed validation or a rolled-back submission reserves no key. Demo seed registrations also receive stable keys and canonical request digests. Retain these fields for the registration's lifetime; storing failed requests or supporting multiple keys per registration is outside MVP scope.

Persist processing row with registration so a crash cannot lose indexing work. Claim rows atomically with PostgreSQL locking; single-worker MVP, bounded queue and lease recovery. Match snapshot replacement and status READY update are one transaction. Verify both registrations' conferences in the service before storing and before returning a match. Hard deletes are not exposed in MVP; future deletion must remove/rebuild search projections too.

Indexes: registration(conference_id), registration_processing(status,next_attempt_at), attendee_session(capability_hash), and unique registration(conference_id,normalized_email). Do not store raw capability or organizer tokens in migrations, fixtures or logs.

## Elasticsearch projection

Alias `wingman-attendees` points to a versioned index such as `wingman-attendees-v1`. Document `_id` is registration UUID, supporting idempotent upsert. Fields:

| Field | Mapping / purpose |
| --- | --- |
| registrationId, conferenceId, attendeeId | keyword; scope and source-of-truth lookup |
| name, jobTitle, company | display text (not email); actual DTO values re-read from PostgreSQL |
| profileText | text; canonical bio + role + company + interests + networking goals |
| interests | keyword array |
| embedding | indexed dense_vector, dims = configured provider dimension, cosine similarity |
| embeddingModel, indexVersion | keyword; prevent mixed embedding spaces |
| updatedAt | date |

Canonical text uses fixed field labels/order and trims whitespace. Embedding and prompts exclude email and all capabilities. Reject zero, nonfinite or dimension-mismatched vectors. kNN pre-filter is a bool containing conferenceId and `must_not attendeeId=self`; ask for k=3, size=3, num_candidates=50. Validate IDs against PostgreSQL before disclosure. Do not present vector score as interpersonal compatibility.

PostgreSQL commit precedes embedding/indexing. Elasticsearch failure leaves a retryable durable registration. Use refresh wait-for for the small demo to ensure read-after-index behavior. Reindex from registered PostgreSQL profiles via a private CLI command, creating a fresh index and swapping alias only after verification. Worker restart may recompute embeddings; no vector copy in PostgreSQL is required for MVP. Retry uses the same document ID. Model changes require full regeneration of all documents before alias switch.

## Migration and seed policy

Planned files: `backend/src/main/resources/db/migration/V1__core_schema.sql`, `V2__matching_state.sql`; production data is never seeded by schema migrations. Demo profile runner loads `backend/src/main/resources/demo/attendees.json` with stable UUIDs: one conference, six varied professional profiles and a second conference with at least one deliberately similar profile to test isolation. Use `example.test` addresses only. Seed repeatedly without duplicate rows; seed must finish real embeddings/indexing before rehearsal. Supply organizer capability through a private environment variable and persist its hash, not a hardcoded token. Local seed summary prints IDs and counts only. Rebuild Elasticsearch after deleting only its disposable volume; registration count must remain unchanged.
